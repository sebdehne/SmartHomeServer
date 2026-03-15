export type FirewallRequestData = {
    serviceState?: ServiceStateUpdate;
    enabledDnsBlockLists?: string[];
}

export type ServiceStateUpdate = {
    service: string;
    state: any;
}

export type FirewallState = {
    serviceStates?: { [key: string]: ServiceState };
    dnsBlockLists?: DnsBlockList[];
}

export type ServiceState = {
    elements: FirewallElement[],
}

export type FirewallElement = {
    name?: string;
    description?: string;
    value?: any;
    type: FirewallElementType;
    writeable: boolean;
    templateElement?: FirewallElement;
}

export type FirewallElementType =
    'string'
    | 'stringList'
    | 'stringNullable'
    | 'number'
    | 'boolean'
    | 'elementList'


export type DnsBlockList = {
    name: string;
    enabled: boolean;
    changedAt: string;
}
