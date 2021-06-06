export class VideoBrowserRequest {
    public minFileSize: number | null;
    public maxFileSize: number | null;
    public fromDate: number | null;
    public toDate: number | null;

    public constructor(minFileSize: number | null, maxFileSize: number | null, fromDate: number | null, toDate: number | null) {
        this.minFileSize = minFileSize;
        this.maxFileSize = maxFileSize;
        this.fromDate = fromDate;
        this.toDate = toDate;
    }
}

export class VideoBrowserResponse {
    public dirs: VideoDirectory[];

    public constructor(dirs: VideoDirectory[]) {
        this.dirs = dirs;
    }
}

export class VideoDirectory {
    public dirname: string;
    public files: VideoFile[];

    public constructor(dirname: string, files: VideoFile[]) {
        this.dirname = dirname;
        this.files = files;
    }
}

export class VideoFile {
    public filename: string;
    public size: number;
    public timestampMillis: number;

    public constructor(filename: string, size: number, timestampMillis: number) {
        this.filename = filename;
        this.size = size;
        this.timestampMillis = timestampMillis;
    }
}