import { Button } from "@mui/material";
import React from "react";
import WebsocketService, { useUserSettings } from "../../Websocket/websocketClient";
import { arrayBufferToBase64 } from "../Utils/utils";

type FirmwareUploadProps = {
    setSending: (sending: boolean) => void;
    setCmdResult: (sending: boolean | null) => void;
}

export const FirmwareUpload = ({ setSending, setCmdResult }: FirmwareUploadProps) => {

    const userSettings = useUserSettings();

    const uploadFirmware = (file: File) => {
        setSending(true);

        const reader = new FileReader();

        reader.onload = ev => {
            const rawData = ev!!.target!!.result as ArrayBuffer;
            const firmwareBased64Encoded = arrayBufferToBase64(rawData);

            WebsocketService.rpc({
                type: "underFloorHeaterRequest",
                underFloorHeaterRequest: {
                    type: "firmwareUpgrade",
                    firmwareBased64Encoded
                }
            }).then(response => {
                setCmdResult(response.underFloorHeaterResponse!!.firmwareUploadSuccess!!);
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

    return (
        <div>
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
                <Button
                    variant="contained"
                    component="span"
                    disabled={!userSettings.userCanAdmin("heaterUnderFloor")}
                >
                    Upload firmware
                </Button>
            </label>
        </div>
    );
};