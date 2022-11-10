

export class EssValues {
    public soc: number;
    public batteryCurrent: number;
    public batteryPower: number;
    public batteryVoltage: number;

    public gridL1: GridData;
    public gridL2: GridData;
    public gridL3: GridData;
    public gridPower: number;

    public outputL1: GridData;
    public outputL2: GridData;
    public outputL3: GridData;
    public outputPower: number;

    public systemState: string;
    public mode: string;
    public acPowerSetPoint: number;
    public maxChargePower: number;
    public maxDischargePower: number;


    constructor(soc: number, batteryCurrent: number, batteryPower: number, batteryVoltage: number, gridL1: GridData, gridL2: GridData, gridL3: GridData, gridPower: number, outputL1: GridData, outputL2: GridData, outputL3: GridData, outputPower: number, systemState: string, mode: string, acPowerSetPoint: number, maxChargePower: number, maxDischargePower: number) {
        this.soc = soc;
        this.batteryCurrent = batteryCurrent;
        this.batteryPower = batteryPower;
        this.batteryVoltage = batteryVoltage;
        this.gridL1 = gridL1;
        this.gridL2 = gridL2;
        this.gridL3 = gridL3;
        this.gridPower = gridPower;
        this.outputL1 = outputL1;
        this.outputL2 = outputL2;
        this.outputL3 = outputL3;
        this.outputPower = outputPower;
        this.systemState = systemState;
        this.mode = mode;
        this.acPowerSetPoint = acPowerSetPoint;
        this.maxChargePower = maxChargePower;
        this.maxDischargePower = maxDischargePower;
    }
}

export class GridData {
    public current: number;
    public power: number;
    public freq: number;
    public s: number;
    public voltage: number;


    constructor(current: number, power: number, freq: number, s: number, voltage: number) {
        this.current = current;
        this.power = power;
        this.freq = freq;
        this.s = s;
        this.voltage = voltage;
    }
}

export class EssRequest {
    public acPowerSetPoint: number | null;
    public maxChargePower: number | null;
    public maxDischargePower: number | null;


    constructor(acPowerSetPoint: number | null, maxChargePower: number | null, maxDischargePower: number | null) {
        this.acPowerSetPoint = acPowerSetPoint;
        this.maxChargePower = maxChargePower;
        this.maxDischargePower = maxDischargePower;
    }
}

