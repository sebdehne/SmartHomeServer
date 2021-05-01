import React, { useEffect, useState } from 'react';
import {
    Button,
    ButtonGroup,
    Container,
    Paper,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableRow
} from "@material-ui/core";
import WebsocketService from "../../Websocket/websocketClient";
import { timeToDelta } from "../GarageDoor/GarageDoor";
import Header from "../Header";
import './HeaterController.css';
import {
    UnderFloorHeaterMode,
    UnderFloorHeaterRequest,
    UnderFloorHeaterRequestType,
    UnderFloorHeaterStatus
} from "../../Websocket/types/UnderFloorHeater";
import { Notify, SubscriptionType } from "../../Websocket/types/Subscription";
import { RequestType, RpcRequest, RpcResponse } from "../../Websocket/types/Rpc";
import { formateDateTime } from "../Utils/dateUtils";

const HeaterController = () => {
    const [underFloorHeaterStatus, setUnderFloorHeaterStatus] = useState<UnderFloorHeaterStatus | null>(null);
    const [sending, setSending] = useState<boolean>(false);
    const [cmdResult, setCmdResult] = useState<boolean | null>(null);
    const [currentSeconds, setCurrentSeconds] = useState(Date.now());

    useEffect(() => {
        setTimeout(() => setCurrentSeconds(Date.now()), 1000)
    }, [currentSeconds])

    const sendUpdate = (underFloorHeaterRequest: UnderFloorHeaterRequest) => {
        setSending(true);
        WebsocketService.rpc(new RpcRequest(
            RequestType.underFloorHeaterRequest,
            null,
            null,
            null,
            underFloorHeaterRequest,
            null,
            null
        )).then((response: RpcResponse) => {
            setCmdResult(response.underFloorHeaterResponse!!.updateUnderFloorHeaterModeSuccess);
            setUnderFloorHeaterStatus(response.underFloorHeaterResponse!!.underFloorHeaterStatus);
            setTimeout(() => {
                setCmdResult(null);
            }, 2000);
        }).finally(() => setSending(false));
    };

    useEffect(() => {
        const subId = WebsocketService.subscribe(
            SubscriptionType.getUnderFloorHeaterStatus,
            (notify: Notify) => setUnderFloorHeaterStatus(notify.underFloorHeaterStatus!!),
            () => {
                WebsocketService.rpc(new RpcRequest(
                    RequestType.underFloorHeaterRequest,
                    null,
                    null,
                    null,
                    new UnderFloorHeaterRequest(
                        UnderFloorHeaterRequestType.getStatus,
                        null,
                        null,
                        null
                    ),
                    null,
                    null))
                    .then(response => setUnderFloorHeaterStatus(response.underFloorHeaterResponse!!.underFloorHeaterStatus));
            }
        )

        return () => WebsocketService.unsubscribe(subId);
    }, []);

    if (underFloorHeaterStatus == null) {
        return <div>Loading...</div>;
    }

    return (
        <Container maxWidth="sm" className="App">
            <Header
                title="Heater Controller"
                sending={sending}
            />

            <div style={{
                display: "flex",
                flexDirection: "row",
                justifyContent: "space-between"
            }}>
                <Button
                    variant="contained"
                    color={
                        underFloorHeaterStatus.mode === UnderFloorHeaterMode.constantTemperature
                            ? 'secondary'
                            : 'primary'
                    }
                    onClick={() => sendUpdate(new UnderFloorHeaterRequest(
                        UnderFloorHeaterRequestType.updateMode,
                        UnderFloorHeaterMode.constantTemperature,
                        null,
                        null
                    ))}>
                    Constant Temp
                </Button>
                <Button
                    variant="contained"
                    color={
                        underFloorHeaterStatus.mode === UnderFloorHeaterMode.permanentOn
                            ? 'secondary'
                            : 'primary'
                    }
                    onClick={() => sendUpdate(new UnderFloorHeaterRequest(
                        UnderFloorHeaterRequestType.updateMode,
                        UnderFloorHeaterMode.permanentOn,
                        null,
                        null
                    ))}>
                    On
                </Button>
                <Button
                    variant="contained"
                    color={
                        underFloorHeaterStatus.mode === UnderFloorHeaterMode.permanentOff
                            ? 'secondary'
                            : 'primary'
                    }
                    onClick={() => sendUpdate(new UnderFloorHeaterRequest(
                        UnderFloorHeaterRequestType.updateMode,
                        UnderFloorHeaterMode.permanentOff,
                        null,
                        null
                    ))}>
                    Off
                </Button>
            </div>

            <TableContainer component={Paper} style={{
                marginTop: "20px"
            }}>
                <Table aria-label="simple table">
                    <TableBody>
                        <TableRow>
                            <TableCell component="th" scope="row">Current status:</TableCell>
                            <TableCell align="right">{underFloorHeaterStatus.status}</TableCell>
                        </TableRow>
                        <TableRow>
                            <TableCell component="th" scope="row">Target temperature:</TableCell>
                            <TableCell align="right">
                                {underFloorHeaterStatus.targetTemperature}  &deg;C <ButtonGroup variant="contained"
                                                                                                aria-label="contained primary button group"
                                                                                                style={{
                                                                                                    margin: "10px"
                                                                                                }}>
                                <Button
                                    disabled={underFloorHeaterStatus.targetTemperature <= 10}
                                    onClick={() => sendUpdate(new UnderFloorHeaterRequest(
                                        UnderFloorHeaterRequestType.updateTargetTemperature,
                                        null,
                                        underFloorHeaterStatus?.targetTemperature - 1,
                                        null
                                    ))}>-</Button>
                                <Button
                                    disabled={underFloorHeaterStatus.targetTemperature >= 50}
                                    onClick={() => sendUpdate(new UnderFloorHeaterRequest(
                                        UnderFloorHeaterRequestType.updateTargetTemperature,
                                        null,
                                        underFloorHeaterStatus?.targetTemperature + 1,
                                        null
                                    ))}>+</Button>
                            </ButtonGroup>
                            </TableCell>
                        </TableRow>
                        {underFloorHeaterStatus.fromController &&
                        <TableRow>
                            <TableCell component="th" scope="row">Current temperature:</TableCell>
                            <TableCell
                                align="right">{underFloorHeaterStatus.fromController.currentTemperature / 100} &deg;C</TableCell>
                        </TableRow>
                        }

                        <TableRow>
                            <TableCell component="th" scope="row">Expensive hours to skip:</TableCell>
                            <TableCell align="right">
                                {underFloorHeaterStatus.mostExpensiveHoursToSkip} <ButtonGroup variant="contained"
                                                                                               aria-label="contained primary button group"
                                                                                               style={{
                                                                                                   margin: "10px"
                                                                                               }}>
                                <Button
                                    disabled={underFloorHeaterStatus.mostExpensiveHoursToSkip <= 1}
                                    onClick={() => sendUpdate(new UnderFloorHeaterRequest(
                                        UnderFloorHeaterRequestType.updateMostExpensiveHoursToSkip,
                                        null,
                                        null,
                                        underFloorHeaterStatus.mostExpensiveHoursToSkip - 1
                                    ))}>-</Button>
                                <Button
                                    disabled={underFloorHeaterStatus.mostExpensiveHoursToSkip >= 24}
                                    onClick={() => sendUpdate(new UnderFloorHeaterRequest(
                                        UnderFloorHeaterRequestType.updateMostExpensiveHoursToSkip,
                                        null,
                                        null,
                                        underFloorHeaterStatus.mostExpensiveHoursToSkip + 1
                                    ))}>+</Button>
                            </ButtonGroup>
                            </TableCell>
                        </TableRow>
                        <TableRow>
                            <TableCell component="th" scope="row">Waiting for low energy price:</TableCell>
                            <TableCell align="right">
                                {underFloorHeaterStatus.waitUntilCheapHour === null ? "No" : formateDateTime(underFloorHeaterStatus.waitUntilCheapHour)}
                            </TableCell>
                        </TableRow>
                        {underFloorHeaterStatus.fromController &&
                        <TableRow>
                            <TableCell component="th" scope="row">Updated:</TableCell>
                            <TableCell
                                align="right">{timeToDelta(currentSeconds, underFloorHeaterStatus.fromController.receivedAt)} ago</TableCell>
                        </TableRow>
                        }
                    </TableBody>
                </Table>
            </TableContainer>

            {cmdResult != null && cmdResult && <p>Sent &#128077;!</p>}
            {cmdResult != null && !cmdResult && <p>Failed &#128078;!</p>}
        </Container>
    );
};

export default HeaterController;
