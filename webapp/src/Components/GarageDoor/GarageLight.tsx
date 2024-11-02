import React, {useEffect, useState} from 'react';
import {Container, FormControlLabel, Radio, RadioGroup} from "@mui/material";
import WebsocketService from "../../Websocket/websocketClient";
import Header from "../Header";
import {GarageLightRequestType, GarageLightStatus} from "../../Websocket/types/Garage";
import {RpcResponse} from "../../Websocket/types/Rpc";
import {Notify} from "../../Websocket/types/Subscription";

export const GarageLight = () => {
    const [garageStatus, setGarageStatus] = useState<GarageLightStatus>();
    const [sending, setSending] = useState<boolean>(false);
    const [cmdResult, setCmdResult] = useState<boolean | null>(null);
    const [currentSeconds, setCurrentSeconds] = useState(Date.now());

    useEffect(() => {
        setTimeout(() => setCurrentSeconds(Date.now()), 1000)
    }, [currentSeconds])

    useEffect(() => {
        const subId = WebsocketService.subscribe(
            "getGarageLightStatus",
            (notify: Notify) => setGarageStatus(notify.garageStatus!!),
            () => {
                WebsocketService.rpc({
                    type: "garageLightRequest",
                    garageLightRequest: {type: "getStatus"}
                }).then(response => setGarageStatus(response.garageLightResponse!!.status));
            }
        )

        return () => WebsocketService.unsubscribe(subId);
    }, []);

    const sendCommand = (cmd: GarageLightRequestType) => {
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
                setGarageStatus(garageLightResponse.status);
            }
        }).finally(() => setSending(false));
    }

    return (
        <Container maxWidth="sm" className="App">
            <Header
                sending={sending}
                title="Garage door controller"
            />

            {!garageStatus && <div>No data available right now</div>}

            {garageStatus &&
                <div>
                    <ul>
                        <li>Ceiling light: <RadioGroup
                            aria-labelledby="demo-radio-buttons-group-label"
                            value={garageStatus.ceilingLightIsOn ? 'switchOnCeilingLight' : 'switchOffCeilingLight'}
                            name="radio-buttons-group"
                            onChange={(event, value) => sendCommand(value as GarageLightRequestType)}
                        >
                            <FormControlLabel value="switchOffCeilingLight" control={<Radio/>} label="Off"/>
                            <FormControlLabel value="switchOnCeilingLight" control={<Radio/>} label="On"/>
                        </RadioGroup></li>
                        <li>Led stripe: <RadioGroup
                            aria-labelledby="demo-radio-buttons-group-label"
                            value={garageStatus.ledStripeStatus}
                            name="radio-buttons-group"
                            onChange={(event, value) => {
                                sendCommand(value === 'off' ? 'switchLedStripeOff' : value === 'onLow' ? 'switchLedStripeOnLow' : 'switchLedStripeOnHigh');
                            }}
                        >
                            <FormControlLabel value="off" control={<Radio/>} label="Off"/>
                            <FormControlLabel value="onLow" control={<Radio/>} label="On Low"/>
                            <FormControlLabel value="onHigh" control={<Radio/>} label="On High"/>
                        </RadioGroup></li>
                        <li>Clock slew: {garageStatus.timestampDelta} seconds</li>
                    </ul>
                    <p>Updated: {timeToDelta(currentSeconds, garageStatus.utcTimestampInMs)} ago</p>
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

