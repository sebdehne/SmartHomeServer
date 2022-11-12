import {
    Accordion,
    AccordionDetails,
    AccordionSummary,
    Button,
    ButtonGroup,
    Container,
    Grid,
    TextField
} from "@material-ui/core";
import Header from "../Header";
import React, { Dispatch, SetStateAction, useEffect, useState } from "react";
import { ESSState, OperationMode, SoCLimit } from "../../Websocket/types/EnergyStorageSystem";
import WebsocketService from "../../Websocket/websocketClient";
import ExpandMoreIcon from "@material-ui/icons/ExpandMore";
import { ProfileSettingsComponent } from "./ProfileSettings";
import { Visualization } from "./Visualization";


export const EnergyStorageSystem = () => {
    const [sending, setSending] = useState<boolean>(false);
    const [essState, setEssState] = useState<ESSState>();

    useEffect(() => {
        const subId = WebsocketService.subscribe("essState", notify => {
                setEssState(notify.essState!!);
            },
            () => WebsocketService.rpc({
                type: "essRead",
            }).then(response => {
                setEssState(response.essState!!);
            }));

        return () => WebsocketService.unsubscribe(subId);
    }, []);

    return <Container maxWidth="sm" className="App">
        <Header
            title={"Energy storage system"}
            sending={sending}
        />
        {essState &&
            <>
                <OperationModeSwitch
                    operationMode={essState.operationMode}
                    setSending={setSending}
                    onNewData={setEssState}
                    soCLimit={essState.soCLimit}
                />

                <Visualization essValues={essState.measurements}/>

                <ProfileSettingsComponent
                    profileSettings={essState.profileSettings}
                    setSending={setSending}
                    onNewData={setEssState}
                />
            </>
        }
    </Container>
}

type OperationModeSwitchProps = {
    operationMode: OperationMode;
    soCLimit: SoCLimit,
    setSending: Dispatch<SetStateAction<boolean>>;
    onNewData: (essState: ESSState) => void;
}
const OperationModeSwitch = ({ soCLimit, operationMode, setSending, onNewData }: OperationModeSwitchProps) => {

    const updateModeTo = (mode: OperationMode) => {
        setSending(true)
        WebsocketService.rpc({
            type: "essWrite",
            essWrite: {
                operationMode: mode
            }
        })
            .then(resp => onNewData(resp.essState!!))
            .finally(() => setSending(false))
    };

    return <Grid container spacing={2} direction="column" alignItems="center">
        <Grid container direction="row">
            <Grid item xs={2}></Grid>
            <Grid item xs={8}>
                <ButtonGroup variant="contained" style={{
                    margin: "10px"
                }}>
                    <Button
                        color={operationMode === "automatic" ? 'secondary' : 'primary'}
                        onClick={() => updateModeTo("automatic")}>Automatic</Button>
                    <Button color={operationMode === "passthrough" ? 'secondary' : 'primary'}
                            onClick={() => updateModeTo("passthrough")}>Passthrough</Button>
                    <Button
                        color={operationMode === "manual" ? 'secondary' : 'primary'}
                        onClick={() => updateModeTo("manual")}
                    >Manual</Button>
                </ButtonGroup>
            </Grid>
            <Grid item xs={2}></Grid>
        </Grid>

        <Grid container direction="row">
            <Grid item xs={12}>
                <SoCLimitComponent
                    soCLimit={soCLimit}
                    setSending={setSending}
                    onNewData={onNewData}
                />
            </Grid>
        </Grid>

    </Grid>
}

type SoCLimitProps = {
    soCLimit: SoCLimit,
    setSending: Dispatch<SetStateAction<boolean>>;
    onNewData: (essState: ESSState) => void;
}

const SoCLimitComponent = ({ soCLimit, setSending, onNewData }: SoCLimitProps) => {
    const [from, setFrom] = useState("");
    const [to, setTo] = useState("");

    const update = (soCLimit: SoCLimit) => {
        setSending(true);
        WebsocketService.rpc({
            type: "essWrite",
            essWrite: {
                soCLimit
            }
        })
            .then(resp => onNewData(resp.essState!!))
            .finally(() => setSending(false))
    }

    return <Accordion>
        <AccordionSummary
            expandIcon={<ExpandMoreIcon/>}
            aria-controls="panel1a-content"
            id="panel1a-header">
            <div
                style={{
                    width: "100%",
                    display: "flex",
                    flexDirection: "row",
                    justifyContent: "space-between"
                }}
            >
                <div>Soc limits:</div>
                <div>{soCLimit.from} - {soCLimit.to} %</div>
            </div>
        </AccordionSummary>
        <AccordionDetails>
            <Grid container spacing={2} direction={"column"}>
                <Grid container spacing={2} direction={"row"} alignItems={"center"}>
                    <Grid item xs={7}>From:</Grid>
                    <Grid item xs={3}><TextField value={from} onChange={e => setFrom(e.target.value.trim())}/></Grid>
                    <Grid item xs={2}><Button variant={"contained"} onClick={() => {
                        const limit: SoCLimit = {
                            from: parseInt(from),
                            to: soCLimit.to
                        }
                        update(limit)
                    }}>Update</Button></Grid>
                </Grid>
                <Grid container spacing={2} direction={"row"} alignItems={"center"}>
                    <Grid item xs={7}>To:</Grid>
                    <Grid item xs={3}><TextField value={to} onChange={e => setTo(e.target.value.trim())}/></Grid>
                    <Grid item xs={2}><Button variant={"contained"} onClick={() => {
                        const limit: SoCLimit = {
                            from: soCLimit.from,
                            to: parseInt(to)
                        }
                        update(limit)
                    }}>Update</Button></Grid>
                </Grid>
            </Grid>
        </AccordionDetails>
    </Accordion>;
}