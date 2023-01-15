import { Button } from "@material-ui/core";
import React from "react";
import WebsocketService, { useUserSettings } from "../../Websocket/websocketClient";
import { arrayBufferToBase64 } from "../Utils/utils";

type FirmwareUploadProps = {
    setSending: (sending: boolean) => void;
    setCmdResult: (sending: boolean | null) => void;
    clientId: string;
}

export const FirmwareUpload = ({ setSending, setCmdResult, clientId }: FirmwareUploadProps) => {

    const userSettings = useUserSettings();

    const uploadFirmware = (file: File) => {
        setSending(true);

        const reader = new FileReader();

        reader.onload = ev => {
            const rawData = ev!!.target!!.result as ArrayBuffer;
            const firmwareBased64Encoded = arrayBufferToBase64(rawData);

            WebsocketService.rpc({
                type: "evChargingStationRequest",
                evChargingStationRequest: { type: "uploadFirmwareToClient", clientId, firmwareBased64Encoded }
            }).then(response => {
                setCmdResult(response.evChargingStationResponse!!.uploadFirmwareToClientResult!!);
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
                    disabled={!userSettings.userCanAdmin("evCharging")}
                    variant="contained"
                    component="span"
                >
                    Upload firmware
                </Button>
            </label>
        </div>
    );
};