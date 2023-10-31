import { Subscribe, Unsubscribe } from "./Subscription";
import { GarageRequest, GarageResponse } from "./Garage";
import { UnderFloorHeaterRequest, UnderFloorHeaterResponse } from "./UnderFloorHeater";
import { EvChargingStationRequest, EvChargingStationResponse } from "./EVChargingStation";
import { EnvironmentSensorRequest, EnvironmentSensorResponse } from "./EnvironmentSensors";
import { VideoBrowserRequest, VideoBrowserResponse } from "./Video";
import { QuickStatsResponse } from "./QuickStats";
import {
    EnergyConsumptionData,
    EnergyConsumptionQuery,
    EnergyPricingSettingsRead,
    EnergyPricingSettingsWrite
} from "./EnergyPricingSettings";
import { ESSState, ESSWrite } from "./EnergyStorageSystem";
import { UserSettings, WriteUserSettings } from "./UserSettings";
import { WriteBms } from "./Bms";
import {StairsHeatingRequest, StairsHeatingResponse} from "./stairsHeating";

export type RequestType =
    "subscribe"
    | "unsubscribe"
    | "garageRequest"
    | "underFloorHeaterRequest"
    | "evChargingStationRequest"
    | "environmentSensorRequest"
    | "videoBrowser"
    | "quickStats"
    | "energyConsumptionQuery"
    | "readEnergyPricingSettings"
    | "writeEnergyPricingSettings"
    | "essRead"
    | "essWrite"
    | "userSettings"
    | "readAllUserSettings"
    | "writeUserSettings"
    | "writeBms"
    | "stairsHeatingRequest"
    ;

export type RpcRequest = {
    type: RequestType;
    subscribe?: Subscribe;
    unsubscribe?: Unsubscribe;

    garageRequest?: GarageRequest;
    underFloorHeaterRequest?: UnderFloorHeaterRequest;
    evChargingStationRequest?: EvChargingStationRequest;
    environmentSensorRequest?: EnvironmentSensorRequest;
    videoBrowserRequest?: VideoBrowserRequest;
    essWrite?: ESSWrite;
    energyPricingSettingsWrite?: EnergyPricingSettingsWrite;
    energyConsumptionQuery?: EnergyConsumptionQuery;
    writeUserSettings?: WriteUserSettings,
    writeBms?: WriteBms,
    stairsHeatingRequest?: StairsHeatingRequest;
}

export type RpcResponse = {
    subscriptionCreated?: boolean;
    subscriptionRemoved?: boolean;

    garageResponse?: GarageResponse;
    underFloorHeaterResponse?: UnderFloorHeaterResponse;
    evChargingStationResponse?: EvChargingStationResponse;
    environmentSensorResponse?: EnvironmentSensorResponse;
    videoBrowserResponse?: VideoBrowserResponse;
    quickStatsResponse?: QuickStatsResponse;
    essState?: ESSState;
    energyPricingSettingsRead?: EnergyPricingSettingsRead;
    userSettings?: UserSettings;
    energyConsumptionData?: EnergyConsumptionData;
    allUserSettings?: { [userId: string]: UserSettings },
    stairsHeatingResponse?: StairsHeatingResponse;
}
