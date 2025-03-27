import React, {useEffect, useState} from 'react';
import {Button, ButtonGroup, Container, FormControlLabel, Radio, RadioGroup, TextField} from "@mui/material";
import WebsocketService, {useUserSettings} from "../../Websocket/websocketClient";
import Header from "../Header";
import {GarageLightRequestType, GarageLightStatus, LightLedMode} from "../../Websocket/types/Garage";
import {RpcResponse} from "../../Websocket/types/Rpc";
import {Notify} from "../../Websocket/types/Subscription";
import {HoermannE4Broadcast, HoermannE4Command, SupramatiDoorState} from "../../Websocket/types/garage.domain";
import dayjs from "dayjs";
import {mdiLightbulb, mdiLightbulbOn} from "@mdi/js";
import Icon from "@mdi/react";
import {theme} from "../../theme";

export const GarageController = () => {
    const [garageLightState, setgarageLightState] = useState<GarageLightStatus>();
    const [garageDoorStatus, setGarageDoorStatus] = useState<HoermannE4Broadcast>();
    const [sending, setSending] = useState<boolean>(false);
    const [cmdResult, setCmdResult] = useState<boolean | null>(null);
    const [currentSeconds, setCurrentSeconds] = useState<number>(Date.now().valueOf());
    const userSettings = useUserSettings();
    const [updateLowMilliVolts, setUpdateLowMilliVolts] = useState(false);
    const [milliVolts, setMilliVolts] = useState('0');

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

    const sendLedStripeMode = (mode: LightLedMode) => {
        setSending(true);
        WebsocketService.rpc({
            type: "garageLightRequest",
            garageLightRequest: {
                type: "setLedStripeMode",
                setLedStripeMode: mode,
            }
        }).then((response: RpcResponse) => {
            const garageLightResponse = response.garageLightResponse!.commandSendSuccess!;
            setCmdResult(garageLightResponse);
            setgarageLightState(response.garageLightResponse!.status);
            setTimeout(() => {
                setCmdResult(null);
            }, 2000);
        }).finally(() => setSending(false));
    };

    const sendSetMilliVolts = (milliVolts: number) => {
        setSending(true);
        WebsocketService.rpc({
            type: "garageLightRequest",
            garageLightRequest: {
                type: "setLedStripeLowMillivolts",
                ledStripeLowMillivolts: milliVolts,
            }
        }).then((response: RpcResponse) => {
            const garageLightResponse = response.garageLightResponse!.commandSendSuccess!;
            setCmdResult(garageLightResponse);
            setgarageLightState(response.garageLightResponse!.status);
            setTimeout(() => {
                setCmdResult(null);
            }, 2000);
        }).finally(() => setSending(false));
    };

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

    const lightIsOn = garageLightState?.ceilingLightIsOn ?? false;

    const garageDoorStatusTimeDelta = Math.round((garageDoorStatus ? (currentSeconds - dayjs(garageDoorStatus.receivedAt).valueOf()) : 0) / 1000);

    return (
        <Container maxWidth="sm" className="App">
            <Header
                sending={sending}
                title="Garage"
            />

            {!garageDoorStatus && <div>No light-status right now</div>}
            {garageDoorStatus && <div style={{display: "flex", flexDirection: "column", alignItems: "center"}}>
                <div style={{display: "flex"}}>
                    <div style={{display: "flex", flexDirection: "column", alignItems: "center"}}>
                        <div style={{padding: '20px'}}>
                            <DoorDrawing currentPos={garageDoorStatus.currentPos} width={100} height={100}/>
                        </div>
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
                        {Math.abs(garageDoorStatusTimeDelta) > 5 && <span>Last update {timeToDeltaSeconds(garageDoorStatusTimeDelta)} ago</span>}
                    </div>
                    <div style={{display: "flex", flexDirection: "column", alignItems: "center"}}>
                        <div style={{
                            minHeight: '100px',
                            padding: '34px'
                        }}>
                            {lightIsOn && <Icon path={mdiLightbulbOn} size={3} style={{color: 'yellow'}}/>}
                            {!lightIsOn && <Icon path={mdiLightbulb} size={3} style={{color: 'gray'}}/>}
                        </div>
                        <ButtonGroup variant="contained" style={{
                            margin: "10px"
                        }}>
                            <Button
                                disabled={!userSettings.userCanWrite("garageDoor")}
                                onClick={() => sendLightCommand(lightIsOn ? "switchOffCeilingLight" : "switchOnCeilingLight")}
                            >Switch {lightIsOn ? 'off' : 'on'}</Button>
                        </ButtonGroup>

                    </div>

                </div>


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


            <h3>LED Light Outside:</h3>
            {!garageLightState && <div>No light-status right now</div>}
            {garageLightState &&
                <div>
                    <div>Status: {garageLightState.ledStripeStatus}</div>
                    <ButtonGroup variant="contained" style={{
                        margin: "10px"
                    }}>
                        <Button
                            disabled={!userSettings.userCanWrite("garageDoor")}
                            color={garageLightState.ledStripeCurrentMode === LightLedMode.manual ? 'secondary' : 'primary'}
                            onClick={() => sendLedStripeMode(LightLedMode.manual)}
                        >Manual</Button>
                        <Button
                            disabled={!userSettings.userCanWrite("garageDoor")}
                            color={garageLightState.ledStripeCurrentMode === LightLedMode.auto ? 'secondary' : 'primary'}
                            onClick={() => sendLedStripeMode(LightLedMode.auto)}
                        >Auto</Button>
                    </ButtonGroup>
                    {garageLightState.ledStripeCurrentMode === LightLedMode.manual && <ul>
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
                    </ul>}

                    <p>Updated: {timeToDelta(currentSeconds, garageLightState.utcTimestampInMs)} ago</p>
                    <p onClick={() => {
                        setMilliVolts(String(garageLightState.ledStripeLowMillivolts));
                        setUpdateLowMilliVolts(true)
                    }}>low milliVolts: {garageLightState.ledStripeLowMillivolts}</p>

                    {updateLowMilliVolts && <div>
                        <h4>Set milliVolts</h4>
                        <TextField
                            value={milliVolts}
                            onChange={event => setMilliVolts(event.target.value)}
                        />
                        <Button onClick={() => {
                            sendSetMilliVolts(parseInt(milliVolts));
                            setUpdateLowMilliVolts(false);
                        }}>Submit</Button>
                    </div>}
                </div>
            }


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

export function timeToDeltaSeconds(deltaInSeconds: number): string {
    if (deltaInSeconds < 60) {
        return `${Math.round(deltaInSeconds)} seconds`;
    } else {
        const min = deltaInSeconds / 60;
        const seconds = deltaInSeconds % 60;
        return `${Math.floor(min)} minutes, ${Math.floor(seconds)} seconds`;
    }
}

const DoorDrawing = ({
                         currentPos,
                         width,
                         height,
                     }: {
    currentPos: number;
    width: number;
    height: number;
}) => {
    const closed = currentPos / 200;

    const wallThinkness = width * 0.1;
    const roofHeight = height * 0.2;
    const doorHeight = height * 0.3;
    const spacingV = height * 0.05;
    const spacingH = width * 0.05;
    const doorDownY = height - ((height - doorHeight- spacingH) * closed)

    return <svg width={width} height={height} xmlns="http://www.w3.org/2000/svg">
        <polygon points={`0,${height} 0,${roofHeight} ${width * 0.5},0 ${width},${roofHeight} ${width},${height} 
        ${width - wallThinkness},${height} ${width - wallThinkness},${doorHeight} ${wallThinkness},${doorHeight} ${wallThinkness},${height}`}
                 style={{fill: theme.palette.primary.main}}/>
        <polygon points={`${wallThinkness + spacingH},${doorDownY} ${wallThinkness + spacingH},${doorHeight + spacingV} 
        ${width - wallThinkness - spacingH},${doorHeight + spacingV} ${width - wallThinkness - spacingH},${doorDownY}`}
                 style={{fill: 'white'}}/>
        <text textAnchor="middle" x={width * 0.5} y={height * 0.24} fill="white">{
            closed === 0 ? 'closed' : closed === 1 ? 'open' : (Math.round(closed * 100) + '%')
        }</text>
    </svg>
};