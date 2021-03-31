import React, { useEffect, useRef, useState } from 'react';
import { Button, Container } from "@material-ui/core";
import './Webcams.css';
import VideoService from "./video";
import { useHistory } from "react-router-dom";
import Header from "../Header";

const streams = [
    "parking1",
    "parking2"
]

const Webcams = () => {
    const refContainer = useRef<HTMLVideoElement>(null);
    const [selectedStream, setSelectedStream] = useState<string>(streams[0]);

    useEffect(() =>
        VideoService.start(selectedStream, stream => {
            refContainer.current!!.srcObject = stream;
        }), [selectedStream]);

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
        </Container>
    );
};

export default Webcams;
