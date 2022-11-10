import Header from "../Header";
import React, { useEffect, useState } from "react";
import {
    Button,
    Container,
    Paper,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableRow,
    TextField
} from "@material-ui/core";
import { EssRequest, EssValues } from "../../Websocket/types/Ess";
import WebsocketService from "../../Websocket/websocketClient";
import { SubscriptionType } from "../../Websocket/types/Subscription";
import { RequestType, RpcRequest } from "../../Websocket/types/Rpc";

export const Energy = () => {

    const [sending, setSending] = useState(false);
    const [essValues, setEssValues] = useState<EssValues | null>(null);
    const [editing, setEditing] = useState<boolean>(false);
    const [acPowerSetPoint, setAcPowerSetPoint] = useState<string>("");
    const [maxChargePower, setMaxChargePower] = useState<string>("");
    const [maxDischargePower, setMaxDischargePower] = useState<string>("");

    useEffect(() => {
        if (!editing && essValues) {
            setAcPowerSetPoint(essValues.acPowerSetPoint.toString());
            setMaxChargePower(essValues.maxChargePower.toString());
            setMaxDischargePower(essValues.maxDischargePower.toString());
        }
    }, [essValues, editing, setAcPowerSetPoint, setMaxChargePower, setMaxDischargePower]);

    const send = (essRequest: EssRequest) => {
        setEditing(false);
        WebsocketService.rpc(new RpcRequest(
            RequestType.essRequest,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            essRequest
        )).then(response => setEssValues(response.essValues))
    }

    useEffect(() => {
        const subId = WebsocketService.subscribe(SubscriptionType.essValues, notify => {
                setEssValues(notify.essValues);
            },
            () => WebsocketService.rpc(new RpcRequest(
                RequestType.essValues,
                null,
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


                        <TableRow>
                            <TableCell component="th" scope="row">
                                AC Power Set Point
                            </TableCell>
                            <TableCell align="right">
                                <TextField value={acPowerSetPoint}
                                           onChange={event => {
                                               setEditing(true);
                                               setAcPowerSetPoint(event.target.value);
                                           }}
                                />
                                <Button onClick={() => {
                                    send(new EssRequest(parseInt(acPowerSetPoint), null, null))
                                }}>Send</Button>
                            </TableCell>
                        </TableRow>
                        <TableRow>
                            <TableCell component="th" scope="row">
                                Max Charge Power
                            </TableCell>
                            <TableCell align="right">
                                <TextField value={maxChargePower}
                                           onChange={event => {
                                               setEditing(true);
                                               setMaxChargePower(event.target.value);
                                           }}
                                />
                                <Button onClick={() => {
                                    send(new EssRequest(null, parseInt(maxChargePower), null))
                                }}>Send</Button>
                            </TableCell>
                        </TableRow>
                        <TableRow>
                            <TableCell component="th" scope="row">
                                Max Discharge Power
                            </TableCell>
                            <TableCell align="right">
                                <TextField value={maxDischargePower}
                                           onChange={event => {
                                               setEditing(true);
                                               setMaxDischargePower(event.target.value);
                                           }}
                                />
                                <Button onClick={() => {
                                    send(new EssRequest(null, null, parseInt(maxDischargePower)))
                                }}>Send</Button>
                            </TableCell>
                        </TableRow>


                    </TableBody>
                </Table>
            </TableContainer>
        </Container>
    )
}