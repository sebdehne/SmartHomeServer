import Header from "../Header";
import React, {useCallback, useEffect, useState} from "react";
import {
    Button,
    Container,
    Divider,
    Paper,
    Switch,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableRow
} from "@mui/material";
import WebsocketClient from "../../Websocket/websocketClient";
import {FirewallState} from "../../Websocket/types/Firewall";

export const Firewall = () => {
    const [sending, setSending] = useState<boolean>(false);
    const [state, setState] = useState<FirewallState>();

    useEffect(() => {
        setSending(true);
        const subId = WebsocketClient.subscribe('firewall', notify => {
            setState(notify.firewallState);
        }, () => {
            setSending(false);
        });
        return () => {
            WebsocketClient.unsubscribe(subId);
        }
    }, []);

    const toggleDnsList = useCallback((list: string) => {
        if (state) {
            let enabled = Object.entries(state.dnsBlockingState.listsToEnabled)
                .filter(([, dnsListState]) => dnsListState.enabled)
                .map(([list,]) => list);

            if (enabled.includes(list)) {
                enabled = enabled.filter(l => l !== list);
            } else {
                enabled.push(list);
            }
            setSending(true);
            WebsocketClient.rpc({
                type: 'dnsBlockingSet',
                dnsBlockingLists: enabled
            }).then(() => setSending(false));
        }
    }, [state]);


    const toggleBlockedMac = useCallback((name: string) => {
        if (state) {
            let blockedNames = state.blockedMacState.blockedMacs
                .filter((d) => d.blocked)
                .map(d => d.name);

            if (blockedNames.includes(name)) {
                blockedNames = blockedNames.filter(l => l !== name);
            } else {
                blockedNames.push(name);
            }
            setSending(true);
            WebsocketClient.rpc({
                type: 'blockedMacsSet',
                blockedMacs: blockedNames
            }).then(() => setSending(false));
        }
    }, [state]);


    return <Container maxWidth="sm" className="App">
        <Header
            title="DNS Blocking"
            sending={sending}
        />

        <Button variant={"contained"} onClick={() => {
            setSending(true);
            WebsocketClient.rpc({
                type: "dnsBlockingUpdateStandardLists"
            }).then(() => setSending(false))
        }}>Update standard lists</Button>

        {state && <TableContainer component={Paper} style={{
            marginTop: "20px"
        }}>
            <Table aria-label="simple table">
                <TableBody>
                    {Object.entries(state.dnsBlockingState.listsToEnabled).map(([list, listState]) => (
                        <TableRow key={list}>
                            <TableCell component="th" scope="row">
                                <div style={{display: "flex", flexDirection: "column", justifyContent: "flex-start"}}>
                                    <span>{list}</span>
                                    <span>{new Date(listState.lastUpdated).toLocaleDateString()}</span>
                                </div>
                            </TableCell>
                            <TableCell align="right">
                                <div style={{
                                    display: "flex",
                                    flexDirection: "row",
                                    alignItems: "center",
                                    justifyContent: "flex-end"
                                }}>
                                    <Switch disabled={sending} checked={!listState.enabled}
                                            onClick={() => toggleDnsList(list)}></Switch>
                                    <span
                                        style={{
                                            width: '80px',
                                            display: "inline"
                                        }}>{listState.enabled ? 'blocked' : 'open'}</span>
                                </div>
                            </TableCell>
                        </TableRow>
                    ))}
                </TableBody>
            </Table>
        </TableContainer>}

        <Divider/>

        <h2 style={{textAlign: "center"}}>Device blocking</h2>

        {state && <TableContainer component={Paper} style={{
            marginTop: "20px"
        }}>
            <Table aria-label="simple table">
                <TableBody>
                    {state.blockedMacState.blockedMacs.map((d) => (
                        <TableRow key={d.name}>
                            <TableCell component="th" scope="row">
                                <span>{d.name}</span>
                            </TableCell>
                            <TableCell align="right">
                                <div style={{
                                    display: "flex",
                                    flexDirection: "row",
                                    alignItems: "center",
                                    justifyContent: "flex-end"
                                }}>
                                    <Switch disabled={sending} checked={!d.blocked}
                                            onClick={() => toggleBlockedMac(d.name)}></Switch>
                                    <span
                                        style={{
                                            width: '80px',
                                            display: "inline"
                                        }}>{d.blocked ? 'blocked' : 'unblocked'}</span>
                                </div>
                            </TableCell>
                        </TableRow>
                    ))}
                </TableBody>
            </Table>
        </TableContainer>}


    </Container>
}

