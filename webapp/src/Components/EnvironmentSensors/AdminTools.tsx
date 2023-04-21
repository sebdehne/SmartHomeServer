import { Button, Grid, Paper, Table, TableBody, TableCell, TableContainer, TableRow } from "@mui/material";
import React from "react";
import WebsocketService, { useUserSettings } from "../../Websocket/websocketClient";
import { EnvironmentSensorRequest, FirmwareInfo } from "../../Websocket/types/EnvironmentSensors";
import { arrayBufferToBase64 } from "../Utils/utils";

type AdminToolsProps = {
    firmwareInfo?: FirmwareInfo;
    setSending: (sending: boolean) => void;
    setCmdResult: (sending: boolean | null) => void;
    setFirmwareInfo: (firmwareInfo?: FirmwareInfo) => void;
    sendUpdate: (req: EnvironmentSensorRequest) => void;
}

export const AdminTools = ({
                               firmwareInfo,
                               setSending,
                               setCmdResult,
                               setFirmwareInfo,
                               sendUpdate
                           }: AdminToolsProps) => {

    const userSettings = useUserSettings();

    const uploadFirmware = (file: File) => {
        setSending(true);

        const reader = new FileReader();

        reader.onload = ev => {
            const rawData = ev!!.target!!.result as ArrayBuffer;
            const firmwareBased64Encoded = arrayBufferToBase64(rawData);

            WebsocketService.rpc({
                type: "environmentSensorRequest",
                environmentSensorRequest: {
                    type: "uploadFirmware",
                    firmwareFilename: file.name,
                    firmwareBased64Encoded
                }
            }).then(response => {
                setCmdResult(true);
                setFirmwareInfo(response.environmentSensorResponse!!.firmwareInfo!!);
                setTimeout(() => {
                    setCmdResult(null);
                }, 2000);
            }).finally(() => setSending(false));

        };
        reader.onerror = ev => {
            console.log("Error reading file:");
            console.log(ev);
        };
        reader.readAsArrayBuffer(file);
    };

    const scheduleTimeAdjustment = () => sendUpdate({
        type: "scheduleTimeAdjustment"
    });
    const scheduleReset = () => sendUpdate({
        type: "scheduleReset"
    });

    return (
        <Grid item xs={12}>
            <Grid container justifyContent="flex-start" spacing={2} alignItems={"center"}>
                <TableContainer component={Paper} style={{
                    marginTop: "20px"
                }}>
                    <Table aria-label="simple table">
                        <TableBody>
                            {firmwareInfo &&
                                <>
                                    <TableRow>
                                        <TableCell component="th" scope="row">Filename</TableCell>
                                        <TableCell align="right">{firmwareInfo.filename}</TableCell>
                                    </TableRow>
                                    <TableRow>
                                        <TableCell component="th" scope="row">Size</TableCell>
                                        <TableCell align="right">{firmwareInfo.size} bytes</TableCell>
                                    </TableRow>
                                </>
                            }
                            <TableRow>
                                <TableCell component="th" scope="row">Upload new</TableCell>
                                <TableCell align="right">
                                    <input
                                        accept=".bin"
                                        hidden
                                        id="firmwareUploadFileSelector"
                                        type="file"
                                        onChange={e => {
                                            uploadFirmware(e.target.files!![0]);
                                        }}
                                    />
                                    <label htmlFor="firmwareUploadFileSelector">
                                        <Button disabled={!userSettings.userCanAdmin("environmentSensors")}
                                                variant="contained" component="span">
                                            Upload firmware
                                        </Button>
                                    </label>
                                </TableCell>
                            </TableRow>
                            <TableRow>
                                <TableCell component="th" scope="row"/>
                                <TableCell align="right">
                                    <Button disabled={!userSettings.userCanAdmin("environmentSensors")}
                                            variant="contained" component="span" onClick={scheduleTimeAdjustment}>
                                        Adjust time for all
                                    </Button>
                                </TableCell>
                            </TableRow>
                            <TableRow>
                                <TableCell component="th" scope="row"/>
                                <TableCell align="right">
                                    <Button disabled={!userSettings.userCanAdmin("environmentSensors")}
                                            variant="contained" component="span" onClick={scheduleReset}>
                                        Reset all
                                    </Button>
                                </TableCell>
                            </TableRow>
                        </TableBody>
                    </Table>
                </TableContainer>
            </Grid>
        </Grid>
    );
};