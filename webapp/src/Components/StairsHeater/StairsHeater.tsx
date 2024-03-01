import Header from "../Header";
import React, {useCallback, useEffect, useState} from "react";
import {Button, Container, Paper, Table, TableBody, TableCell, TableContainer, TableRow} from "@mui/material";
import WebsocketClient from "../../Websocket/websocketClient";
import {StairsHeatingResponse, StairsHeatingType} from "../../Websocket/types/stairsHeating";
import {timeToDelta} from "../GarageDoor/GarageDoor";
import dayjs from "dayjs";

export const StairsHeater = () => {
    const [currentMilliSeconds, setCurrentMilliSeconds] = useState(dayjs().valueOf());
    const [sending, setSending] = useState<boolean>(false);
    const [data, setData] = useState<StairsHeatingResponse>();

    useEffect(() => {
        setTimeout(() => setCurrentMilliSeconds(dayjs().valueOf()), 1000);
    }, [currentMilliSeconds]);

    useEffect(() => {
        setSending(true);
        WebsocketClient.rpc(
            {
                type: "stairsHeatingRequest",
                stairsHeatingRequest: {
                    type: "get"
                }
            }
        ).then(resp => {
            setData(resp.stairsHeatingResponse);
            setSending(false);
        })
    }, [setData]);

    const exec = useCallback((type: StairsHeatingType) => {
        setSending(true);
        WebsocketClient.rpc({
            type: "stairsHeatingRequest",
            stairsHeatingRequest: {
                type: type
            }
        }).then(resp => {
            setSending(false);
            setData(resp.stairsHeatingResponse);
        })
    }, [setData]);

    return (
        <Container maxWidth="sm" className="App">
            <Header
                title="Stair Heater"
                sending={sending}
            />

            {data?.data && <TableContainer component={Paper} style={{
                marginTop: "20px"
            }}>
                <Table aria-label="simple table">
                    <TableBody>
                        <TableRow>
                            <TableCell component="th" scope="row">Updated:</TableCell>
                            <TableCell
                                align="right">{timeToDelta(currentMilliSeconds, dayjs(data.data.createdAt).valueOf())} ago</TableCell>
                        </TableRow>
                        <TableRow>
                            <TableCell component="th" scope="row">State:</TableCell>
                            <TableCell
                                align="right">{data.data.currentState ? 'On' : 'Off'}</TableCell>
                        </TableRow>
                        <TableRow>
                            <TableCell component="th" scope="row">Stairs temperature:</TableCell>
                            <TableCell
                                align="right">{(data.data.temperature).toFixed(2)} &deg;C</TableCell>
                        </TableRow>
                        <TableRow>
                            <TableCell component="th" scope="row">Ampere:</TableCell>
                            <TableCell
                                align="right">{(data.data.current).toFixed(2)} A</TableCell>
                        </TableRow>
                        <TableRow>
                            <TableCell component="th" scope="row">Power:</TableCell>
                            <TableCell
                                align="right">{(data.data.current * 230).toFixed(2)} Watt</TableCell>
                        </TableRow>
                        <TableRow>
                            <TableCell component="th" scope="row">Enable/Disable:</TableCell>
                            <TableCell
                                align="right">
                                <div>
                                    <span style={{margin: '10px'}}>{data.settings.enabled ? 'Enabled' : 'Disable'}</span>
                                    <Button variant={"contained"} onClick={() => exec("enableDisable")}>{data.settings.enabled ? 'Disable' : 'Enable'}</Button>
                                </div>
                            </TableCell>
                        </TableRow>
                        <TableRow>
                            <TableCell component="th" scope="row">Outside temperature range:</TableCell>
                            <TableCell
                                align="right">
                                <div style={{
                                    display: "flex",
                                    flexDirection: "row",
                                    alignItems: "center",
                                    justifyContent: "flex-end"
                                }}>
                                    <Button variant={"contained"} onClick={() => exec("decreaseOutsideLowerTemp")}>-</Button>
                                    <span
                                        style={{margin: '10px'}}>{(data.settings.outsideTemperatureRangeFrom).toFixed(0)}</span>
                                    <Button variant={"contained"} onClick={() => exec("increaseOutsideLowerTemp")}>+</Button>
                                    <span style={{margin: '10px'}}>-</span>
                                    <Button variant={"contained"} onClick={() => exec("decreaseOutsideUpperTemp")}>-</Button>
                                    <span
                                        style={{margin: '10px'}}>{(data.settings.outsideTemperatureRangeTo).toFixed(0)}</span>
                                    <Button variant={"contained"}  onClick={() => exec("increaseOutsideUpperTemp")}>+</Button>
                                </div>
                            </TableCell>
                        </TableRow>
                        <TableRow>
                            <TableCell component="th" scope="row">Target temperature:</TableCell>
                            <TableCell
                                align="right">
                                <div style={{
                                    display: "flex",
                                    flexDirection: "row",
                                    alignItems: "center",
                                    justifyContent: "flex-end"
                                }}>
                                    <Button variant={"contained"} onClick={() => exec("decreaseTargetTemp")}>-</Button>
                                    <span
                                        style={{margin: '10px'}}>{(data.settings.targetTemperature).toFixed(0)}</span>
                                    <Button variant={"contained"} onClick={() => exec("increaseTargetTemp")}>+</Button>
                                </div>
                            </TableCell>
                        </TableRow>
                    </TableBody>
                </Table>
            </TableContainer>
            }


        </Container>
    )
}