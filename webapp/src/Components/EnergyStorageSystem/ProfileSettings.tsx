import React, { Dispatch, SetStateAction, useState } from "react";
import { ESSState, ProfileSettings } from "../../Websocket/types/EnergyStorageSystem";
import { Accordion, AccordionDetails, AccordionSummary, Button, Grid, TextField } from "@material-ui/core";
import ExpandMoreIcon from "@material-ui/icons/ExpandMore";
import WebsocketClient, { useUserSettings } from "../../Websocket/websocketClient";

export type ProfileSettingsProps = {
    profileSettings: ProfileSettings[];
    setSending: Dispatch<SetStateAction<boolean>>;
    onNewData: (essState: ESSState) => void;
}

export const ProfileSettingsComponent = ({ profileSettings, setSending, onNewData }: ProfileSettingsProps) => {
    return <Grid container spacing={1} direction={"column"}>
        <Grid item xs={12}><h3>Profiles:</h3></Grid>
        {profileSettings.map(ps => <Grid item xs={12} key={ps.profileType}>
            <ProfileSetting
                settings={ps}
                setSending={setSending}
                onNewData={onNewData}
            />
        </Grid>)}
    </Grid>
};


type ProfileSettingProps = {
    settings: ProfileSettings,
    setSending: Dispatch<SetStateAction<boolean>>;
    onNewData: (essState: ESSState) => void;
}
const ProfileSetting = ({ settings, setSending, onNewData }: ProfileSettingProps) => {
    const [acPowerSetPoint, setAcPowerSetPoint] = useState(settings.acPowerSetPoint.toString());
    const [maxChargePower, setMaxChargePower] = useState(settings.maxChargePower.toString());
    const [maxDischargePower, setMaxDischargePower] = useState(settings.maxDischargePower.toString());
    const userSettings = useUserSettings();

    const write = (obj: any) => {
        setSending(true);
        WebsocketClient.rpc({
            type: "essWrite",
            essWrite: {
                updateProfile: { ...settings, ...obj }
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
                <div>{settings.profileType}</div>
                <div>{settings.acPowerSetPoint} W</div>
            </div>
        </AccordionSummary>
        <AccordionDetails>
            <div style={{ display: "flex", flexDirection: "column", width: "100%" }}>
                <div style={{ width: "100%", display: "flex", flexDirection: "row", justifyContent: "space-between" }}>
                    <div>Ac power set point:</div>
                    <div>
                        <TextField value={acPowerSetPoint}
                                   onChange={e => setAcPowerSetPoint(e.target.value.trim())}/>
                        <Button
                            disabled={!userSettings.userCanWrite("energyStorageSystem")}
                            variant={"contained"} onClick={() => {
                            write({ acPowerSetPoint: parseInt(acPowerSetPoint) })
                        }}>Update</Button>
                    </div>
                </div>
                <div style={{ width: "100%", display: "flex", flexDirection: "row", justifyContent: "space-between" }}>
                    <div>Max charge power:</div>
                    <div>
                        <TextField value={maxChargePower} onChange={e => setMaxChargePower(e.target.value.trim())}/>
                        <Button
                            disabled={!userSettings.userCanWrite("energyStorageSystem")}
                            variant={"contained"} onClick={() => {
                            write({ maxChargePower: parseInt(maxChargePower) })
                        }}>Update</Button>
                    </div>
                </div>
                <div style={{ width: "100%", display: "flex", flexDirection: "row", justifyContent: "space-between" }}>
                    <div>Max discharge power:</div>
                    <div>
                        <TextField value={maxDischargePower}
                                   onChange={e => setMaxDischargePower(e.target.value.trim())}/>
                        <Button
                            disabled={!userSettings.userCanWrite("energyStorageSystem")}
                            variant={"contained"} onClick={() => {
                            write({ maxDischargePower: parseInt(maxDischargePower) })
                        }}>Update</Button>
                    </div>
                </div>
            </div>
        </AccordionDetails>
    </Accordion>
}