import React, { useEffect, useState } from 'react';
import { Button, Container, TextField } from "@material-ui/core";
import WebsocketService from "../../Websocket/websocketClient";
import { timeToDelta } from "../GarageDoor/GarageDoor";
import Header from "../Header";
import './HeaterController.css';
import { ArrowDownward, ArrowUpward } from "@material-ui/icons";
import {
    UnderFloorHeaterMode,
    UnderFloorHeaterRequest,
    UnderFloorHeaterRequestType,
    UnderFloorHeaterStatus,
    UpdateUnderFloorHeaterMode
} from "../../Websocket/types/UnderFloorHeater";
import { Notify, SubscriptionType } from "../../Websocket/types/Subscription";
import { RequestType, RpcRequest, RpcResponse } from "../../Websocket/types/Rpc";
import { formateDateTime } from "../Utils/dateUtils";

const HeaterController = () => {
    const [underFloorHeaterStatus, setUnderFloorHeaterStatus] = useState<UnderFloorHeaterStatus | null>(null);
    const [sending, setSending] = useState<boolean>(false);
    const [cmdResult, setCmdResult] = useState<boolean | null>(null);
    const [currentSeconds, setCurrentSeconds] = useState(Date.now());
    const [targetTemperatur, setTargetTemperatur] = useState(25);

    useEffect(() => {
        setTimeout(() => setCurrentSeconds(Date.now()), 1000)
    }, [currentSeconds])

    const onNewStatus = (heaterStatus: UnderFloorHeaterStatus | null) => {
        if (heaterStatus != null) {
            setUnderFloorHeaterStatus(heaterStatus);
            if (heaterStatus) {
                setTargetTemperatur(Math.round(heaterStatus.constantTemperatureStatus.targetTemperature) / 100);
            }
        }
    }

    useEffect(() => {
        const subId = WebsocketService.subscribe(
            SubscriptionType.getUnderFloorHeaterStatus,
            (notify: Notify) => onNewStatus(notify.underFloorHeaterStatus!!),
            () => {
                WebsocketService.rpc(new RpcRequest(
                    RequestType.underFloorHeaterRequest,
                    null,
                    null,
                    null,
                    new UnderFloorHeaterRequest(
                        UnderFloorHeaterRequestType.getUnderFloorHeaterStatus,
                        null
                    ),
                    null))
                    .then(response => onNewStatus(response.underFloorHeaterResponse!!.underFloorHeaterStatus));
            }
        )

        return () => WebsocketService.unsubscribe(subId);
    }, []);

    const sendUpdate = (underFloorHeaterMode: UnderFloorHeaterMode) => {
        setSending(true);
        WebsocketService.rpc(new RpcRequest(
            RequestType.underFloorHeaterRequest,
            null,
            null,
            null,
            new UnderFloorHeaterRequest(
                UnderFloorHeaterRequestType.updateUnderFloorHeaterMode,
                new UpdateUnderFloorHeaterMode(
                    underFloorHeaterMode,
                    targetTemperatur * 100,
                    null
                )
            ),
            null
        )).then((response: RpcResponse) => {
            setCmdResult(response.underFloorHeaterResponse!!.updateUnderFloorHeaterModeSuccess);
            onNewStatus(response.underFloorHeaterResponse!!.underFloorHeaterStatus);
            setTimeout(() => {
                setCmdResult(null);
            }, 2000);

            // refresh
            WebsocketService.rpc(new RpcRequest(
                RequestType.underFloorHeaterRequest,
                null,
                null,
                null,
                new UnderFloorHeaterRequest(
                    UnderFloorHeaterRequestType.getUnderFloorHeaterStatus,
                    null
                ),
                null))
                .then(response => onNewStatus(response.underFloorHeaterResponse!!.underFloorHeaterStatus));

        }).finally(() => setSending(false));
    };

    return (
        <Container maxWidth="sm" className="App">
            <Header
                title="Heater Controller"
                sending={sending}
            />

            {underFloorHeaterStatus &&
            <div>
                <ul>
                    <li>Current status: {underFloorHeaterStatus.status}</li>
                    <li>Current mode: {underFloorHeaterStatus.mode}</li>
                    <li>Current temperature: {underFloorHeaterStatus.currentTemperature / 100}&deg;C</li>
                    <li>Waiting for low energy
                        price: {underFloorHeaterStatus.constantTemperatureStatus.waitUntilCheapHour === null ? "No" : formateDateTime(underFloorHeaterStatus.constantTemperatureStatus.waitUntilCheapHour)}</li>
                </ul>
            </div>
            }
            {!underFloorHeaterStatus && <p>Status currently not available</p>}

            <div className="Buttons">
                <p>Target temp:</p>
                <div className="TargetTempButtonsContainer">
                    <Button variant="contained" color="primary" onClick={() => {
                        setTargetTemperatur((prev: number) => {
                            return prev + 1;
                        });
                    }}>
                        <ArrowUpward/>
                    </Button>
                    <TextField
                        value={targetTemperatur}
                    />
                    <Button variant="contained" color="primary" onClick={() => {
                        setTargetTemperatur((prev: number) => {
                            return prev - 1;
                        });
                    }}>
                        <ArrowDownward/>
                    </Button>
                </div>
                <Button variant="contained" color="primary"
                        onClick={() => sendUpdate(UnderFloorHeaterMode.constantTemperature)}>
                    Constant Temp
                </Button>
                <Button variant="contained" color="primary"
                        onClick={() => sendUpdate(UnderFloorHeaterMode.permanentOn)}>
                    On
                </Button>
                <Button variant="contained" color="primary"
                        onClick={() => sendUpdate(UnderFloorHeaterMode.permanentOff)}>
                    Off
                </Button>
            </div>

            {cmdResult != null && cmdResult && <p>Sent &#128077;!</p>}
            {cmdResult != null && !cmdResult && <p>Failed &#128078;!</p>}

            {
                underFloorHeaterStatus &&
                <p>Updated: {timeToDelta(currentSeconds, underFloorHeaterStatus.utcTimestampInMs)} ago</p>
            }

        </Container>
    );
};

export default HeaterController;
