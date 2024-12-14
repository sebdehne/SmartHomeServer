import React, {useEffect, useState} from 'react';
import {Button, ButtonGroup, Container, FormControlLabel, Radio, RadioGroup} from "@mui/material";
import WebsocketService, {useUserSettings} from "../../Websocket/websocketClient";
import Header from "../Header";
import {GarageLightRequestType, GarageLightStatus} from "../../Websocket/types/Garage";
import {RpcResponse} from "../../Websocket/types/Rpc";
import {Notify} from "../../Websocket/types/Subscription";
import {HoermannE4Broadcast, HoermannE4Command, SupramatiDoorState} from "../../Websocket/types/garage.domain";
import dayjs from "dayjs";

export const GarageController = () => {
    const [garageLightState, setgarageLightState] = useState<GarageLightStatus>();
    const [garageDoorStatus, setGarageDoorStatus] = useState<HoermannE4Broadcast>();
    const [sending, setSending] = useState<boolean>(false);
    const [cmdResult, setCmdResult] = useState<boolean | null>(null);
    const [currentSeconds, setCurrentSeconds] = useState(Date.now());
    const userSettings = useUserSettings();

    useEffect(() => {
        setTimeout(() => setCurrentSeconds(Date.now()), 1000)
    }, [currentSeconds])

    useEffect(() => {
        const subId = WebsocketService.subscribe(
            "getGarageLightStatus",
            (notify: Notify) => setgarageLightState(notify.garageStatus!!),
            () => {
                WebsocketService.rpc({
                    type: "garageLightRequest",
                    garageLightRequest: {type: "getStatus"}
                }).then(response => setgarageLightState(response.garageLightResponse!!.status));
            }
        )

        return () => WebsocketService.unsubscribe(subId);
    }, []);
    useEffect(() => {
        const subId = WebsocketService.subscribe(
            "getGarageDoorStatus",
            (notify: Notify) => setGarageDoorStatus(notify.hoermannE4Broadcast!!),
        )

        return () => WebsocketService.unsubscribe(subId);
    }, []);

    const sendDoorCommand = (cmd: HoermannE4Command) => {
        setSending(true);
        WebsocketService.rpc({
            type: "sendHoermannE4Command",
            hoermannE4Command: cmd
        }).then((response: RpcResponse) => {
            const garageLightResponse = response.hoermannE4CommandResult!!;
            setCmdResult(garageLightResponse);
            setTimeout(() => {
                setCmdResult(null);
            }, 2000);
        }).finally(() => setSending(false));
    }

    const sendLightCommand = (cmd: GarageLightRequestType) => {
        setSending(true);
        WebsocketService.rpc({
            type: "garageLightRequest",
            garageLightRequest: {
                type: cmd,
            }
        }).then((response: RpcResponse) => {
            const garageLightResponse = response.garageLightResponse!!;
            setCmdResult(!!garageLightResponse.commandSendSuccess);
            setTimeout(() => {
                setCmdResult(null);
            }, 2000);
            if (garageLightResponse.status) {
                setgarageLightState(garageLightResponse.status);
            }
        }).finally(() => setSending(false));
    }

    return (
        <Container maxWidth="sm" className="App">
            <Header
                sending={sending}
                title="Garage door controller"
            />

            <h3>Light:</h3>
            {!garageLightState && <div>No light-status right now</div>}
            {garageLightState &&
                <div>
                    <ul>
                        <li>Ceiling light: <RadioGroup
                            aria-labelledby="demo-radio-buttons-group-label"
                            value={garageLightState.ceilingLightIsOn ? 'switchOnCeilingLight' : 'switchOffCeilingLight'}
                            name="radio-buttons-group"
                            onChange={(event, value) => sendLightCommand(value as GarageLightRequestType)}
                        >
                            <FormControlLabel value="switchOffCeilingLight" control={<Radio/>} label="Off"/>
                            <FormControlLabel value="switchOnCeilingLight" control={<Radio/>} label="On"/>
                        </RadioGroup></li>
                        <li>Led stripe: <RadioGroup
                            aria-labelledby="demo-radio-buttons-group-label"
                            value={garageLightState.ledStripeStatus}
                            name="radio-buttons-group"
                            onChange={(event, value) => {
                                sendLightCommand(value === 'off' ? 'switchLedStripeOff' : value === 'onLow' ? 'switchLedStripeOnLow' : 'switchLedStripeOnHigh');
                            }}
                        >
                            <FormControlLabel value="off" control={<Radio/>} label="Off"/>
                            <FormControlLabel value="onLow" control={<Radio/>} label="On Low"/>
                            <FormControlLabel value="onHigh" control={<Radio/>} label="On High"/>
                        </RadioGroup></li>
                        <li>Clock slew: {garageLightState.timestampDelta} seconds</li>
                    </ul>
                    <p>Updated: {timeToDelta(currentSeconds, garageLightState.utcTimestampInMs)} ago</p>
                </div>
            }

            <h3>Door:</h3>
            {!garageDoorStatus && <div>No light-status right now</div>}
            {garageDoorStatus && <div>
                <ButtonGroup variant="contained" style={{
                    margin: "10px"
                }}>
                    <Button
                        disabled={!userSettings.userCanWrite("garageDoor")}
                        color={garageDoorStatus.doorState === SupramatiDoorState.CLOSED ? 'secondary' : 'primary'}
                        onClick={() => sendDoorCommand(HoermannE4Command.Open)}
                    >Open</Button>
                    <Button
                        disabled={!userSettings.userCanWrite("garageDoor")}
                        color={garageDoorStatus.doorState === SupramatiDoorState.OPEN ? 'secondary' : 'primary'}
                        onClick={() => sendDoorCommand(HoermannE4Command.Close)}
                    >Close</Button>
                </ButtonGroup>
                <ul>
                    <li>State: {garageDoorStatus.doorState} ({garageDoorStatus.currentPos} / {garageDoorStatus.targetPos})</li>
                    <li>Vented: {garageDoorStatus.isVented ? "Yes" : "No"}</li>
                    <li>MotorSpeed: {garageDoorStatus.motorSpeed}</li>
                    <li>Light: {garageDoorStatus.light ? "On" : "Off"}</li>
                    <li>MotorRunning: {garageDoorStatus.motorRunning ? "Yes" : "No"}</li>
                    <li>Updated: {timeToDelta(currentSeconds, dayjs(garageDoorStatus.receivedAt).valueOf())} ago</li>
                </ul>

                <ButtonGroup variant="outlined" style={{
                    margin: "10px"
                }}>
                    <Button
                        disabled={!userSettings.userCanWrite("garageDoor")}
                        color={'primary'}
                        onClick={() => sendDoorCommand(HoermannE4Command.Toggle)}
                    >Toggle</Button>
                    <Button
                        disabled={!userSettings.userCanWrite("garageDoor")}
                        color={'primary'}
                        onClick={() => sendDoorCommand(HoermannE4Command.Vent)}
                    >Vent</Button>
                    <Button
                        disabled={!userSettings.userCanWrite("garageDoor")}
                        color={'primary'}
                        onClick={() => sendDoorCommand(HoermannE4Command.Light)}
                    >Light</Button>
                    <Button
                        disabled={!userSettings.userCanWrite("garageDoor")}
                        color={'primary'}
                        onClick={() => sendDoorCommand(HoermannE4Command.HalvOpen)}
                    >HalvOpen</Button>
                </ButtonGroup>
            </div>}

            {cmdResult != null && cmdResult && <p>Sent &#128077;!</p>}
            {cmdResult != null && !cmdResult && <p>Failed &#128078;!</p>}

        </Container>
    );
};

export function timeToDelta(left: number, right: number): string {
    const deltaInSeconds = (left - right) / 1000;
    if (deltaInSeconds < 60) {
        return `${Math.round(deltaInSeconds)} seconds`;
    } else {
        const min = deltaInSeconds / 60;
        const seconds = deltaInSeconds % 60;
        return `${Math.floor(min)} minutes, ${Math.floor(seconds)} seconds`;
    }
}

