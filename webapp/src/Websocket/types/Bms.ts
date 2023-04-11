export type WriteBms = {
    type: WriteBmsType,
    bmsId: string,
    soc: number | undefined,
}

export type WriteBmsType = 'writeSoc'

export type BmsId = {
    usbId: string;
    bmsId: string;
    displayName: string;
    capacity: number;
}


export type BmsData = {
    bmsId: BmsId;
    timestamp: string;
    voltage: number;
    current: number;
    soc: number;
    avgEstimatedSoc: number;

    maxCellVoltage: number;
    maxCellNumber: number;
    minCellVoltage: number;
    minCellNumber: number;

    maxTemp: number;
    maxTempCellNumber: number;
    minTemp: number;
    minTempCellNumber: number;

    status: BmStatus;
    mosfetCharging: Boolean;
    mosfetDischarging: Boolean;

    lifeCycles: number;
    remainingCapacity: number; // in Ah

    chargerStatus: Boolean;
    loadStatus: Boolean;

    cycles: number;

    cellVoltages: number[];
    socEstimates: number[];
    errors: string[];
}

export type BmStatus = 'stationary' | 'charged' | 'discharged';