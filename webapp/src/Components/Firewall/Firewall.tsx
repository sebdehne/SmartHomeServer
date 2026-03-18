import Header from "../Header";
import React, {useCallback, useEffect, useState} from "react";
import {
    Button, ButtonGroup,
    Container,
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
import {FirewallService} from "./FirewallService";

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
            let enabled = (state.dnsBlockLists ?? [])
                .filter((dnsListState) => dnsListState.enabled)
                .map((dnsListState) => dnsListState.name);

            if (enabled.includes(list)) {
                enabled = enabled.filter(l => l !== list);
            } else {
                enabled.push(list);
            }
            setSending(true);
            WebsocketClient.rpc({
                type: 'firewallSetDnsEnabledBlockLists',
                firewallRequestData: {
                    enabledDnsBlockLists: enabled,
                }
            }).then(() => setSending(false));
        }
    }, [state]);


    return <Container maxWidth="sm" className="App">
        <Header
            title="Firewall"
            sending={sending}
        />

        <ButtonGroup>
            <Button variant={"contained"} onClick={() => {
                setSending(true);
                WebsocketClient.rpc({
                    type: "firewallRefetchKnownLists"
                }).then(() => setSending(false))
            }}>Update standard DNS lists</Button>
            <Button variant={"contained"} onClick={() => {
                setSending(true);
                WebsocketClient.rpc({
                    type: "firewallRefreshCachedState"
                }).then(() => setSending(false))
            }}>Reload</Button>

        </ButtonGroup>

        <h3>Dns block</h3>

        {state && <TableContainer component={Paper} style={{
            marginTop: "20px"
        }}>
            <Table aria-label="simple table">
                <TableBody>
                    {(state.dnsBlockLists ?? []).map((list) => (
                        <TableRow key={list.name}>
                            <TableCell component="th" scope="row">
                                <div style={{display: "flex", flexDirection: "column", justifyContent: "flex-start"}}>
                                    <span>{list.name}</span>
                                    <span
                                        style={{color: "#5e5e5e"}}>{new Date(list.changedAt).toLocaleDateString()}</span>
                                </div>
                            </TableCell>
                            <TableCell align="right">
                                <div style={{
                                    display: "flex",
                                    flexDirection: "row",
                                    alignItems: "center",
                                    justifyContent: "flex-end"
                                }}>
                                    <Switch disabled={sending} checked={!list.enabled}
                                            onClick={() => toggleDnsList(list.name)}></Switch>
                                    <span
                                        style={{
                                            width: '80px',
                                            display: "inline"
                                        }}>{list.enabled ? 'blocked' : 'open'}</span>
                                </div>
                            </TableCell>
                        </TableRow>
                    ))}
                </TableBody>
            </Table>
        </TableContainer>}

        <h3>Services:</h3>

        {state?.serviceStates && Object.entries(state.serviceStates).map(([serviceName, serviceState]) => (
            <FirewallService key={serviceName} name={serviceName} state={serviceState}/>
        ))}

    </Container>
}

