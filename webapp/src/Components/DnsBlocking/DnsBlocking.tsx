import Header from "../Header";
import React, {useCallback, useEffect, useState} from "react";
import {Button, Container, Paper, Switch, Table, TableBody, TableCell, TableContainer, TableRow} from "@mui/material";
import WebsocketClient from "../../Websocket/websocketClient";
import {DnsBlockingState} from "../../Websocket/types/DnsBlockingState";

export const DnsBlocking = () => {
    const [sending, setSending] = useState<boolean>(false);
    const [state, setState] = useState<DnsBlockingState>();

    useEffect(() => {
        setSending(true);
        const subId = WebsocketClient.subscribe('dnsBlockingGet', notify => {
            setState(notify.dnsBlockingState);
        }, () => {
            setSending(false);
        });
        return () => {
            WebsocketClient.unsubscribe(subId);
        }
    }, []);

    const toggle = useCallback((list: string) => {
        if (state) {
            let enabled = Object.entries(state.listsToEnabled)
                .filter(([list, dnsListState]) => dnsListState.enabled)
                .map(([list, ]) => list);

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
                    {Object.entries(state.listsToEnabled).map(([list, listState]) => (
                        <TableRow key={list}>
                            <TableCell component="th" scope="row">
                                <span>{list}</span>
                            </TableCell>
                            <TableCell align={"left"}>
                                <span>{new Date(listState.lastUpdated).toLocaleString()}</span>
                            </TableCell>
                            <TableCell align="right">
                                <div style={{
                                    display: "flex",
                                    flexDirection: "row",
                                    alignItems: "center",
                                    justifyContent: "flex-end"
                                }}>
                                    <Switch checked={!listState.enabled} onClick={event => toggle(list)}></Switch>
                                    <span
                                        style={{width: '80px', display: "inline"}}>{listState.enabled ? 'blocked' : 'open'}</span>
                                </div>
                            </TableCell>
                        </TableRow>
                    ))}
                </TableBody>
            </Table>
        </TableContainer>}


    </Container>
}

