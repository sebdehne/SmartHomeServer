import React, { useEffect, useRef, useState } from 'react';
import { Button, Checkbox, Container } from "@mui/material";
import './Webcams.css';
import VideoService from "./video";
import Header from "../Header";

const streams = [
    "parking1",
    "parking2"
]

const Webcams = () => {
    const refContainer = useRef<HTMLVideoElement>(null);
    const [selectedStream, setSelectedStream] = useState<string>(streams[0]);
    const [debug, setDebug] = useState(false);
    const [logs, setLogs] = useState<string[]>([]);

    useEffect(() =>
        VideoService.start(selectedStream, stream => {
                refContainer.current!!.srcObject = stream;
            },
            log =>
                setLogs(prevState => ([
                    ...prevState,
                    log
                ]))
        ), [selectedStream]);

    return (
        <Container maxWidth="lg" className="App">
            <Header
                title={"Cameras (" + selectedStream + ")"}
                sending={false}
            />

            {streams.map(s => (
                <Button
                    variant="contained"
                    key={s}
                    color="primary"
                    onClick={() => setSelectedStream(s)}
                >{s}</Button>
            ))}
            <video width="100%" autoPlay muted controls ref={refContainer}/>

            <div>
                <Checkbox
                    checked={debug}
                    onChange={(event, checked) => setDebug(checked)}
                /> Debug
            </div>
            {debug && <div>
                <h2>Logs:</h2>
                <ul>
                    {logs.map(value => (<li><pre>{value}</pre></li>))}
                </ul>
            </div>}

        </Container>
    );
};

export default Webcams;
