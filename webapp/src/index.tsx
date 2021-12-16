import React from 'react';
import ReactDOM from 'react-dom';
import './index.css';
import App from './Components/App/App';
import reportWebVitals from './reportWebVitals';
import { CssBaseline, MuiThemeProvider } from "@material-ui/core";
import { BrowserRouter as Router, Route, Routes } from 'react-router-dom';
import GarageDoor from "./Components/GarageDoor/GarageDoor";
import HeaterController from "./Components/Heater/HeaterController";
import { EvChargingStations } from "./Components/EvChargingStations/EvChargingStations";
import Webcams from "./Components/Webcams/Webcams";
import theme from "./theme";
import { EnvironmentSensors } from "./Components/EnvironmentSensors/EnvironmentSensors";
import { VideoRecordings } from "./Components/VideoRecordings/VideoRecordings";

ReactDOM.render(
    <React.StrictMode>
        <MuiThemeProvider theme={theme}>
            <CssBaseline/>
            <Router basename={process.env.REACT_APP_BASENAME}>
                <Routes>
                    <Route path="/">
                        <App/>
                    </Route>
                    <Route path="/garage">
                        <GarageDoor/>
                    </Route>
                    <Route path="/heater">
                        <HeaterController/>
                    </Route>
                    <Route path="/evChargingStations">
                        <EvChargingStations/>
                    </Route>
                    <Route path="/environmentSensors">
                        <EnvironmentSensors/>
                    </Route>
                    <Route path="/webcams">
                        <Webcams/>
                    </Route>
                    <Route path="/recordings">
                        <VideoRecordings/>
                    </Route>
                </Routes>
            </Router>
        </MuiThemeProvider>
    </React.StrictMode>,
    document.getElementById('root')
);

// If you want to start measuring performance in your app, pass a function
// to log results (for example: reportWebVitals(console.log))
// or send to an analytics endpoint. Learn more: https://bit.ly/CRA-vitals
reportWebVitals();
