const start = (streamId: string, onStream: (stream: MediaStream) => void): (() => void) => {
    let stream = new MediaStream();
    let config = {
        iceServers: [{
            urls: ["stun:stun.l.google.com:19302"]
        }]
    };
    const pc = new RTCPeerConnection(config);

    pc.addTransceiver("video", {
        'direction': 'sendrecv'
    });

    //send ping becouse PION not handle RTCSessionDescription.close()
    let sendChannel = pc.createDataChannel('foo');
    sendChannel.onclose = () => console.log('sendChannel has closed');
    sendChannel.onopen = () => {
        console.log('sendChannel has opened');
        sendChannel.send('ping');
        setInterval(() => {
            sendChannel.send('ping');
        }, 1000)
    }
    sendChannel.onmessage = e => console.log(`Message from DataChannel '${sendChannel.label}' payload '${e.data}'`);

    pc.onnegotiationneeded = () => {
        pc.createOffer()
            .then(offer => pc.setLocalDescription(offer))
            .then(() => {
                const data = new URLSearchParams();
                data.append("suuid", streamId);
                data.append("data", btoa(pc.localDescription!!.sdp));
                return fetch('webrtc/receiver/' + streamId, {
                    method: "POST",
                    body: data
                });
            })
            .then(response => response.text())
            .then(text => {
                pc.setRemoteDescription(new RTCSessionDescription({
                    type: 'answer',
                    sdp: atob(text)
                }))
            })
            .catch(e => console.log(e))
    };
    let track: MediaStreamTrack;
    pc.ontrack = ev => {
        track = ev.track;
        stream.addTrack(ev.track);
        onStream(stream);
    };

    return () => {
        sendChannel.close();
        pc.close();
        stream.removeTrack(track);
    };
};

const VideoService = {
    start
};

export default VideoService;

