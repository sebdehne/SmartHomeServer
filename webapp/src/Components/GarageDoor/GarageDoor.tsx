import React from 'react';
import {Button, Container, Paper, Typography} from "@material-ui/core";
import {useHistory} from "react-router-dom";

const GarageDoor = () => {

    let history = useHistory();

    return (
        <Container maxWidth="sm" className="App">
            <Paper>
                <Button variant="text" color="secondary" onClick={() => {
                    history.push("/")
                }}>
                    Home
                </Button>

                <Typography>
                    <h2>Garage door controller</h2>
                </Typography>

            </Paper>
        </Container>
    );
};

export default GarageDoor;
