export type VideoBrowserRequest = {
    minFileSize?: number;
    maxFileSize?: number;
    fromDate?: number;
    toDate?: number;
}

export type VideoBrowserResponse = {
    dirs: VideoDirectory[];
}

export type VideoDirectory = {
    dirname: string;
    files: VideoFile[];
}

export type VideoFile = {
    filename: string;
    size: number;
    timestampMillis: number;
}