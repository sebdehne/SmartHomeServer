

export type DnsBlockingState = {
    listsToEnabled: { [key: string]: DnsBlockingListState };
}

export type DnsBlockingListState = {
    enabled: boolean;
    lastUpdated: string;
}
