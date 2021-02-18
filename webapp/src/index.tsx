import React from 'react';
import ReactDOM from 'react-dom';
import './index.css';
import App from './Components/App/App';
import reportWebVitals from './reportWebVitals';
import {CssBaseline} from "@material-ui/core";
import {BrowserRouter as Router, Route, Switch} from 'react-router-dom';
import GarageDoor from "./Components/GarageDoor/GarageDoor";
import HeaterController from "./Components/Heater/HeaterController";
import EVChargingStationTester from "./Components/EVChargingStationsManager/EVChargingStationsManager";

ReactDOM.render(
    <React.StrictMode>
        <CssBaseline/>

        <Router basename={process.env.REACT_APP_BASENAME}>
            <Switch>
                <Route exact path="/">
                    <App/>
                </Route>
                <Route path="/garage">
                    <GarageDoor/>
                </Route>
                <Route path="/heater">
                    <HeaterController/>
                </Route>
                <Route path="/evTester">
                    <EVChargingStationTester/>
                </Route>
            </Switch>
        </Router>

    </React.StrictMode>,
    document.getElementById('root')
);

// If you want to start measuring performance in your app, pass a function
// to log results (for example: reportWebVitals(console.log))
// or send to an analytics endpoint. Learn more: https://bit.ly/CRA-vitals
reportWebVitals();
