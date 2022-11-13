import {
    Accordion,
    AccordionDetails,
    AccordionSummary,
    Button,
    ButtonGroup,
    Container,
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

                <Visualization essValues={essState.measurements} essState={essState.essState}/>

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

    return <div style={{ display: "flex", flexDirection: "column" }}>
        <div style={{ width: "100%", display: "flex", flexDirection: "row", justifyContent: "center" }}>
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
        </div>

        <div style={{ width: "100%", display: "flex", flexDirection: "row", justifyContent: "center" }}>
            <SoCLimitComponent
                soCLimit={soCLimit}
                setSending={setSending}
                onNewData={onNewData}
            />
        </div>

    </div>
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

    return <Accordion style={{ width: "100%" }}>
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
            <div style={{ display: "flex", flexDirection: "column", width: "100%" }}>
                <div style={{ display: "flex", flexDirection: "row", justifyContent: "space-between" }}>
                    <div>From:</div>
                    <div>
                        <TextField value={from} onChange={e => setFrom(e.target.value.trim())}/>
                        <Button variant={"contained"} onClick={() => {
                            const limit: SoCLimit = {
                                from: parseInt(from),
                                to: soCLimit.to
                            }
                            update(limit)
                        }}>Update</Button>
                    </div>
                </div>
                <div style={{ display: "flex", flexDirection: "row", justifyContent: "space-between" }}>
                    <div>To:</div>
                    <div>
                        <TextField value={to} onChange={e => setTo(e.target.value.trim())}/>
                        <Button variant={"contained"} onClick={() => {
                            const limit: SoCLimit = {
                                from: soCLimit.from,
                                to: parseInt(to)
                            }
                            update(limit)
                        }}>Update</Button>
                    </div>
                </div>

            </div>
        </AccordionDetails>
    </Accordion>;
}