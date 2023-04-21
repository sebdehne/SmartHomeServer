import {
    Accordion,
    AccordionDetails,
    AccordionSummary,
    Button,
    ButtonGroup,
    Container,
    Grid,
    TextField
} from "@mui/material";
import Header from "../Header";
import React, { Dispatch, SetStateAction, useCallback, useEffect, useState } from "react";
import { ESSState, OperationMode, SoCLimit } from "../../Websocket/types/EnergyStorageSystem";
import WebsocketService from "../../Websocket/websocketClient";
import WebsocketClient, { useUserSettings } from "../../Websocket/websocketClient";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import { ProfileSettingsComponent } from "./ProfileSettings";
import { Visualization } from "./Visualization";
import { BmsData } from "../../Websocket/types/Bms";


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

                <Grid container spacing={1} direction={"column"}>
                    <Grid item xs={12}><h3>BMSes:</h3></Grid>
                    <Grid item xs={12}>
                        {essState.bmsData
                            .sort((a, b) => a.bmsId.displayName.localeCompare(b.bmsId.displayName))
                            .map((bmsData) => (<Bms bmsdata={bmsData} setSending={setSending}/>))}
                    </Grid>
                </Grid>

                <ProfileSettingsComponent
                    profileSettings={essState.profileSettings}
                    setSending={setSending}
                    onNewData={setEssState}
                />
            </>
        }
    </Container>
}

type BmsProps = {
    bmsdata: BmsData,
    setSending: Dispatch<SetStateAction<boolean>>;
}
const Bms = ({ bmsdata, setSending }: BmsProps) => {
    const [socInput, setSocInput] = useState(bmsdata.avgEstimatedSoc.toString());
    const userSettings = useUserSettings();

    const writeSoc = useCallback((soc: number) => {
        setSending(true);
        WebsocketClient.rpc({
            type: "writeBms",
            writeBms: {
                type: "writeSoc",
                bmsId: bmsdata.bmsId.bmsId,
                soc
            }
        })
            .finally(() => setSending(false))
    }, []);

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
                <div>{bmsdata.bmsId.displayName}</div>
                <div>{bmsdata.soc} %</div>
            </div>
        </AccordionSummary>
        <AccordionDetails>
            <div style={{ display: "flex", flexDirection: "column", width: "100%" }}>
                <div style={{ width: "100%", display: "flex", flexDirection: "row", justifyContent: "space-between" }}>
                    <div>Pack voltage:</div>
                    <div>{bmsdata.voltage} V</div>
                </div>
                <div style={{ width: "100%", display: "flex", flexDirection: "row", justifyContent: "space-between" }}>
                    <div>Pack current:</div>
                    <div>{bmsdata.current} V</div>
                </div>
                <div style={{ width: "100%", display: "flex", flexDirection: "row", justifyContent: "space-between" }}>
                    <div>Estimated SoC:</div>
                    <div>{bmsdata.avgEstimatedSoc} %</div>
                </div>
                <div style={{ width: "100%", display: "flex", flexDirection: "row", justifyContent: "space-between" }}>
                    <div>SoC:</div>
                    <div>{bmsdata.soc} %</div>
                </div>
                <div style={{ width: "100%", display: "flex", flexDirection: "row", justifyContent: "space-between" }}>
                    <div>Status:</div>
                    <div>{bmsdata.status}</div>
                </div>
                <div style={{ width: "100%", display: "flex", flexDirection: "row", justifyContent: "space-between" }}>
                    <div>MaxCellVoltage:</div>
                    <div>{bmsdata.maxCellVoltage} V</div>
                </div>
                <div style={{ width: "100%", display: "flex", flexDirection: "row", justifyContent: "space-between" }}>
                    <div>MaxCellNumber:</div>
                    <div>{bmsdata.maxCellNumber}</div>
                </div>
                <div style={{ width: "100%", display: "flex", flexDirection: "row", justifyContent: "space-between" }}>
                    <div>MinCellVoltage:</div>
                    <div>{bmsdata.minCellVoltage} V</div>
                </div>
                <div style={{ width: "100%", display: "flex", flexDirection: "row", justifyContent: "space-between" }}>
                    <div>MinCellNumber:</div>
                    <div>{bmsdata.minCellNumber}</div>
                </div>
                <div style={{ width: "100%", display: "flex", flexDirection: "row", justifyContent: "space-between" }}>
                    <div>Cycles:</div>
                    <div>{bmsdata.cycles}</div>
                </div>
                <div style={{ width: "100%", display: "flex", flexDirection: "row", justifyContent: "space-between" }}>
                    <div>LifeCycles:</div>
                    <div>{bmsdata.lifeCycles}</div>
                </div>
                <div style={{ width: "100%", display: "flex", flexDirection: "row", justifyContent: "space-between" }}>
                    <div>Timestamp:</div>
                    <div>{bmsdata.timestamp}</div>
                </div>
                <div>
                    <TextField value={socInput}
                               onChange={e => setSocInput(e.target.value.trim())}/>
                    <Button
                        disabled={!userSettings.userCanWrite("energyStorageSystem")}
                        variant={"contained"} onClick={() => {
                        writeSoc(parseInt(socInput))
                    }}>Update</Button>
                </div>
            </div>
        </AccordionDetails>
    </Accordion>
}

type OperationModeSwitchProps = {
    operationMode: OperationMode;
    soCLimit: SoCLimit,
    setSending: Dispatch<SetStateAction<boolean>>;
    onNewData: (essState: ESSState) => void;
}
const OperationModeSwitch = ({ soCLimit, operationMode, setSending, onNewData }: OperationModeSwitchProps) => {

    const userSettings = useUserSettings();

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
                    disabled={!userSettings.userCanWrite("energyStorageSystem")}
                    color={operationMode === "automatic" ? 'secondary' : 'primary'}
                    onClick={() => updateModeTo("automatic")}>Automatic</Button>
                <Button
                    disabled={!userSettings.userCanWrite("energyStorageSystem")}
                    color={operationMode === "passthrough" ? 'secondary' : 'primary'}
                    onClick={() => updateModeTo("passthrough")}>Passthrough</Button>
                <Button
                    disabled={!userSettings.userCanWrite("energyStorageSystem")}
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
    const userSettings = useUserSettings();

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
                        <Button
                            disabled={!userSettings.userCanWrite("energyStorageSystem")}
                            variant={"contained"} onClick={() => {
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
                        <Button
                            disabled={!userSettings.userCanWrite("energyStorageSystem")}
                            variant={"contained"} onClick={() => {
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