import React from 'react';
import './index.css';
import App from './Components/App/App';
import {CssBaseline, ThemeProvider} from "@mui/material";
import {BrowserRouter, Route, Routes} from 'react-router-dom';
import GarageDoor from "./Components/GarageDoor/GarageDoor";
import HeaterController from "./Components/Heater/HeaterController";
import {EvChargingStations} from "./Components/EvChargingStations/EvChargingStations";
import Webcams from "./Components/Webcams/Webcams";
import {EnvironmentSensors} from "./Components/EnvironmentSensors/EnvironmentSensors";
import {VideoRecordings} from "./Components/VideoRecordings/VideoRecordings";
import {EnergyPricingSettings} from "./Components/EnergyPricingSettings/EnergyPricingSettings";
import {EnergyStorageSystem} from "./Components/EnergyStorageSystem/EnergyStorageSystem";
import {createRoot} from 'react-dom/client';
import {UserSettingsAdmin} from "./Components/UserSettingsAdmin/UserSettingsAdmin";
import {StairsHeater} from "./Components/StairsHeater/StairsHeater";
import {EnergyStorageSystemV2} from "./Components/EnergyStorageSystemV2/EnergyStorageSystemV2";
import {Firewall} from "./Components/Firewall/Firewall";
import {theme} from "./theme";


const container = document.getElementById('root');

const root = createRoot(container!);

root.render(
    <React.StrictMode>
        <ThemeProvider theme={theme}>
            <CssBaseline/>
            <BrowserRouter basename={import.meta.env.BASE_URL}>
                <Routes>
                    <Route path="/" element={<App/>}/>
                    <Route path="/garage" element={<GarageDoor/>}/>
                    <Route path="/heater" element={<HeaterController/>}/>
                    <Route path="/stairs" element={<StairsHeater/>}/>
                    <Route path="/evChargingStations" element={<EvChargingStations/>}/>
                    <Route path="/environmentSensors" element={<EnvironmentSensors/>}/>
                    <Route path="/webcams" element={<Webcams/>}/>
                    <Route path="/recordings" element={<VideoRecordings/>}/>
                    <Route path="/users" element={<UserSettingsAdmin/>}/>
                    <Route path="/energy_price_settings" element={<EnergyPricingSettings/>}/>
                    <Route path="/energy" element={<EnergyStorageSystem/>}/>
                    <Route path="/energy2" element={<EnergyStorageSystemV2/>}/>
                    <Route path="/firewall" element={<Firewall/>}/>
                </Routes>
            </BrowserRouter>
        </ThemeProvider>
    </React.StrictMode>
);

