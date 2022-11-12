import React, { Dispatch, SetStateAction, useState } from "react";
import { ESSState, ProfileSettings, SoCLimit } from "../../Websocket/types/EnergyStorageSystem";
import { Accordion, AccordionDetails, AccordionSummary, Button, Grid, TextField } from "@material-ui/core";
import ExpandMoreIcon from "@material-ui/icons/ExpandMore";
import WebsocketClient from "../../Websocket/websocketClient";

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
            <Grid container spacing={2} direction={"column"}>
                <Grid container spacing={2} direction={"row"} alignItems={"center"}>
                    <Grid item xs={7}>Ac power set point:</Grid>
                    <Grid item xs={3}><TextField value={acPowerSetPoint}
                                                 onChange={e => setAcPowerSetPoint(e.target.value.trim())}/></Grid>
                    <Grid item xs={2}><Button variant={"contained"} onClick={() => {
                        write({acPowerSetPoint: parseInt(acPowerSetPoint)})
                    }}>Update</Button></Grid>
                </Grid>
                <Grid container spacing={2} direction={"row"} alignItems={"center"}>
                    <Grid item xs={7}>Max charge power:</Grid>
                    <Grid item xs={3}><TextField value={maxChargePower} onChange={e => setMaxChargePower(e.target.value.trim())}/></Grid>
                    <Grid item xs={2}><Button variant={"contained"} onClick={() => {
                        write({maxChargePower: parseInt(maxChargePower)})
                    }}>Update</Button></Grid>
                </Grid>
                <Grid container spacing={2} direction={"row"} alignItems={"center"}>
                    <Grid item xs={7}>Max discharge power:</Grid>
                    <Grid item xs={3}><TextField value={maxDischargePower} onChange={e => setMaxDischargePower(e.target.value.trim())}/></Grid>
                    <Grid item xs={2}><Button variant={"contained"} onClick={() => {
                        write({maxDischargePower: parseInt(maxDischargePower)})
                    }}>Update</Button></Grid>
                </Grid>
            </Grid>
        </AccordionDetails>
    </Accordion>
}