import React, {useEffect, useState} from 'react';
import {Button, CircularProgress, Container, Paper, TextField, Typography} from "@material-ui/core";
import {useHistory} from "react-router-dom";
import {
    Notify,
    RequestType,
    RpcRequest,
    RpcResponse,
    UnderFloorHeaterMode,
    UnderFloorHeaterStatus,
    UpdateUnderFloorHeaterMode
} from "../../api";
import WebsocketService from "../../websocketClient";
import {timeToDelta} from "../GarageDoor/GarageDoor";
import ConnectionStatusComponent from "../ConnectionStatus";
import './HeaterController.css';
import {ArrowDownward, ArrowUpward} from "@material-ui/icons";

const HeaterController = () => {
    let history = useHistory();
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
            RequestType.getUnderFloorHeaterStatus,
            (notify: Notify) => onNewStatus(notify.underFloorHeaterStatus!!),
            () => {
                WebsocketService.rpc(new RpcRequest(RequestType.getUnderFloorHeaterStatus, null, null, null, null, null))
                    .then(response => onNewStatus(response.underFloorHeaterStatus!!));
            }
        )

        return () => WebsocketService.unsubscribe(subId);
    }, []);

    const sendUpdate = (underFloorHeaterMode: UnderFloorHeaterMode) => {
        setSending(true);
        WebsocketService.rpc(new RpcRequest(
            RequestType.updateUnderFloorHeaterMode,
            null,
            null,
            new UpdateUnderFloorHeaterMode(
                underFloorHeaterMode,
                targetTemperatur * 100,
                null
            ),
            null,
            null
        )).then((response: RpcResponse) => {
            setCmdResult(response.updateUnderFloorHeaterModeSuccess);
            onNewStatus(response.underFloorHeaterStatus)
            setTimeout(() => {
                setCmdResult(null);
            }, 2000);

            // refresh
            WebsocketService.rpc(new RpcRequest(RequestType.getUnderFloorHeaterStatus, null, null, null, null, null))
                .then(response => onNewStatus(response.underFloorHeaterStatus!!));

        }).finally(() => setSending(false));
    };

    return (
        <Container maxWidth="sm" className="App">
            <Paper>
                <ConnectionStatusComponent/>
                <Button variant="text" color="secondary" onClick={() => {
                    history.push("/")
                }}>
                    Home
                </Button>
                <Typography>
                    <h2>Heater Controller</h2>
                </Typography>

                {underFloorHeaterStatus &&
                <div>
                    <ul>
                        <li>Current status: {underFloorHeaterStatus.status}</li>
                        <li>Current mode: {underFloorHeaterStatus.mode}</li>
                        <li>Current temperature: {underFloorHeaterStatus.currentTemperature / 100}&deg;C</li>
                        <li>Current energy price too
                            expensive: {underFloorHeaterStatus.constantTemperatureStatus.energyPriceCurrentlyTooExpensive ? 'Yes' : 'No'}</li>
                    </ul>
                </div>
                }
                {!underFloorHeaterStatus && <p>Status currently not available</p>}

                <div className="Buttons">
                    <p>Target temp:</p>
                    <div className="TargetTempButtonsContainer">
                        <Button variant="contained" color="secondary" onClick={() => {
                            setTargetTemperatur((prev: number) => {
                                return prev + 1;
                            });
                        }}>
                            <ArrowUpward/>
                        </Button>
                        <TextField
                            value={targetTemperatur}
                        />
                        <Button variant="contained" color="secondary" onClick={() => {
                            setTargetTemperatur((prev: number) => {
                                return prev - 1;
                            });
                        }}>
                            <ArrowDownward/>
                        </Button>
                    </div>
                    <Button variant="contained" color="secondary"
                            onClick={() => sendUpdate(UnderFloorHeaterMode.constantTemperature)}>
                        Constant Temp
                    </Button>
                    <Button variant="contained" color="secondary"
                            onClick={() => sendUpdate(UnderFloorHeaterMode.permanentOn)}>
                        On
                    </Button>
                    <Button variant="contained" color="secondary"
                            onClick={() => sendUpdate(UnderFloorHeaterMode.permanentOff)}>
                        Off
                    </Button>
                    {sending &&
                    <CircularProgress color="secondary"/>
                    }
                </div>

                {cmdResult != null && cmdResult && <p>Sent &#128077;!</p>}
                {cmdResult != null && !cmdResult && <p>Failed &#128078;!</p>}

                {
                    underFloorHeaterStatus &&
                    <p>Updated: {timeToDelta(currentSeconds, underFloorHeaterStatus.utcTimestampInMs)} ago</p>
                }


            </Paper>
        </Container>
    );
};

export default HeaterController;
