
export type FirewallState = {
    dnsBlockingState: DnsBlockingState;
    blockedMacState: BlockedMacState;
}

export type BlockedMacState = {
    blockedMacs: BlockedMac[];
}

export type BlockedMac = {
    name: string;
    blocked: boolean;
}


export type DnsBlockingState = {
    listsToEnabled: { [key: string]: DnsBlockingListState };
}

export type DnsBlockingListState = {
    enabled: boolean;
    lastUpdated: string;
}
