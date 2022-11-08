import Header from "../Header";
import React, { useEffect, useState } from "react";
import { Container, Paper, Table, TableBody, TableCell, TableContainer, TableRow } from "@material-ui/core";
import { EssValues } from "../../Websocket/types/Ess";
import WebsocketService from "../../Websocket/websocketClient";
import { SubscriptionType } from "../../Websocket/types/Subscription";
import { RequestType, RpcRequest } from "../../Websocket/types/Rpc";

export const Energy = () => {

    const [sending, setSending] = useState(false);
    const [essValues, setEssValues] = useState<EssValues | null>(null);

    useEffect(() => {
        const subId = WebsocketService.subscribe(SubscriptionType.essValues, notify => {
                setEssValues(notify.essValues);
            },
            () => WebsocketService.rpc(new RpcRequest(
                RequestType.quickStats,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
            )).then(response => {
                setEssValues(response.essValues);
            }));

        return () => WebsocketService.unsubscribe(subId);
    }, []);

    return (
        <Container maxWidth="sm" className="App">
            <Header
                title={"Energy Storage System"}
                sending={sending}
            />
            <TableContainer component={Paper} style={{
                marginTop: "20px"
            }}>
                <Table aria-label="simple table">
                    <TableBody>
                        <TableRow>
                            <TableCell component="th" scope="row">
                                Grid power
                            </TableCell>
                            <TableCell align="right">
                                {new Intl.NumberFormat('nb-NO', {
                                    style: 'decimal',
                                    minimumFractionDigits: 2,
                                    maximumFractionDigits: 2
                                }).format(essValues?.gridPower ?? 0)} W
                            </TableCell>
                        </TableRow>
                        <TableRow>
                            <TableCell component="th" scope="row">
                                Output power
                            </TableCell>
                            <TableCell align="right">
                                {new Intl.NumberFormat('nb-NO', {
                                    style: 'decimal',
                                    minimumFractionDigits: 2,
                                    maximumFractionDigits: 2
                                }).format(essValues?.outputPower ?? 0)} W
                            </TableCell>
                        </TableRow>
                        <TableRow>
                            <TableCell component="th" scope="row">
                                Battery power
                            </TableCell>
                            <TableCell align="right">
                                {new Intl.NumberFormat('nb-NO', {
                                    style: 'decimal',
                                    minimumFractionDigits: 2,
                                    maximumFractionDigits: 2
                                }).format(essValues?.batteryPower ?? 0)} W
                            </TableCell>
                        </TableRow>
                        <TableRow>
                            <TableCell component="th" scope="row">
                                Battery Voltage
                            </TableCell>
                            <TableCell align="right">
                                {new Intl.NumberFormat('nb-NO', {
                                    style: 'decimal',
                                    minimumFractionDigits: 2,
                                    maximumFractionDigits: 2
                                }).format(essValues?.batteryVoltage ?? 0)} V
                            </TableCell>
                        </TableRow>
                        <TableRow>
                            <TableCell component="th" scope="row">
                                Battery Current
                            </TableCell>
                            <TableCell align="right">
                                {new Intl.NumberFormat('nb-NO', {
                                    style: 'decimal',
                                    minimumFractionDigits: 2,
                                    maximumFractionDigits: 2
                                }).format(essValues?.batteryCurrent ?? 0)} A
                            </TableCell>
                        </TableRow>
                        <TableRow>
                            <TableCell component="th" scope="row">
                                Battery SoC
                            </TableCell>
                            <TableCell align="right">
                                {new Intl.NumberFormat('nb-NO', {
                                    style: 'decimal',
                                    minimumFractionDigits: 2,
                                    maximumFractionDigits: 2
                                }).format(essValues?.soc ?? 0)} %
                            </TableCell>
                        </TableRow>
                        <TableRow>
                            <TableCell component="th" scope="row">
                                State
                            </TableCell>
                            <TableCell align="right">
                                {essValues?.systemState}
                            </TableCell>
                        </TableRow>
                        <TableRow>
                            <TableCell component="th" scope="row">
                                Mode
                            </TableCell>
                            <TableCell align="right">
                                {essValues?.mode}
                            </TableCell>
                        </TableRow>
                    </TableBody>
                </Table>
            </TableContainer>
        </Container>
    )
}