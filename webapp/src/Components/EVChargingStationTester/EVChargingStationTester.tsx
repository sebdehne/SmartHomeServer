import React, {useState} from 'react';
import {Button, CircularProgress, Container, Paper, Typography} from "@material-ui/core";
import {useHistory} from "react-router-dom";
import {RequestType, RpcRequest} from "../../api";
import WebsocketService from "../../websocketClient";
import ConnectionStatusComponent from "../ConnectionStatus";

const EVChargingStationTester = () => {
    let history = useHistory();
    const [sending, setSending] = useState<boolean>(false);
    const [cmdResult, setCmdResult] = useState<boolean | null>(null);
    const [clientId] = useState<number>(1);
    const [firmwareVersion, setFirmwareVersion] = useState<number | null>();

    const getFirmwareVersion = () => {
        setSending(true);
        WebsocketService.rpc(
            new RpcRequest(
                RequestType.getEvCharingStationFirmwareVersion,
                null,
                null,
                null,
                null,
                clientId!!
            )
        ).then(response => {
            setFirmwareVersion(response.evCharingStationFirmwareVersion);
            setCmdResult(response.evCharingStationFirmwareVersion != null);
        }).finally(() => setSending(false));
    }

    return (
        <Container maxWidth="sm" className="App">
            <Paper>
                <ConnectionStatusComponent/>
                <Button variant="text" color="secondary" onClick={() => {
                    history.push("/")
                }}>
                    Home
                </Button>

                <Typography>
                    <h2 style={{display: "inline"}}>EV Charing station tester</h2>
                    {sending &&
                    <CircularProgress color="secondary"/>
                    }
                </Typography>

                <div>
                    <ul>
                        <li>Firmware: {firmwareVersion}</li>
                    </ul>
                </div>

                <Button variant="contained" color="secondary" onClick={getFirmwareVersion}>
                    Get
                </Button>

                {cmdResult != null && cmdResult && <p>Sent &#128077;!</p>}
                {cmdResult != null && !cmdResult && <p>Failed &#128078;!</p>}

            </Paper>
        </Container>
    );
};

export default EVChargingStationTester;
