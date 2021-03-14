import React, { useEffect, useRef, useState } from 'react';
import { Button, Container, Typography } from "@material-ui/core";
import './Webcams.css';
import VideoService from "./video";
import { useHistory } from "react-router-dom";

const streams = [
    "parking1",
    "parking2"
]

const Webcams = () => {
    let history = useHistory();

    const refContainer = useRef<HTMLVideoElement>(null);
    const [selectedStream, setSelectedStream] = useState<string>(streams[0]);

    useEffect(() =>
        VideoService.start(selectedStream, stream => {
            refContainer.current!!.srcObject = stream;
        }), [selectedStream]);

    return (
        <Container maxWidth="lg" className="App">

            <Button variant="text" color="secondary" onClick={() => {
                history.push("/")
            }}>
                Home
            </Button>

            <Typography>
                <h2>Cameras ({selectedStream})</h2>
            </Typography>

            {streams.map(s => (
                <Button
                    key={s}
                    color="secondary"
                    onClick={() => setSelectedStream(s)}
                >{s}</Button>
            ))}
            <video width="100%" autoPlay muted controls ref={refContainer}/>
        </Container>
    );
};

export default Webcams;
