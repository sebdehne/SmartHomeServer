const start = (streamId: string, onStream: (stream: MediaStream) => void, addLog: (log: string) => void): (() => void) => {
    addLog('init');
    let stream = new MediaStream();
    let config = {
        iceServers: [{
            urls: ["stun:stun.l.google.com:19302"]
        }],
        sdpSemantics: 'unified-plan'
    };
    const pc = new RTCPeerConnection(config);

    pc.addTransceiver("video", {
        'direction': 'sendrecv'
    });

    //send ping becouse PION not handle RTCSessionDescription.close()
    let sendChannel = pc.createDataChannel('foo');
    sendChannel.onclose = () => addLog("sendChannel has closed");
    sendChannel.onopen = () => {
        addLog('sendChannel has opened');
        sendChannel.send('ping');
        setInterval(() => {
            sendChannel.send('ping');
        }, 1000)
    }
    sendChannel.onmessage = e => addLog(`Message from DataChannel '${sendChannel.label}' payload '${e.data}'`);

    pc.onnegotiationneeded = () => {
        pc.createOffer()
            .then(offer => {
                return pc.setLocalDescription(offer);
            })
            .then(() => {
                const localSdpOffer = pc.localDescription!!.sdp;
                addLog('Sending offer: ' + localSdpOffer);
                const data = new URLSearchParams();
                data.append("data", btoa(localSdpOffer));
                return fetch('webrtc/receiver/' + streamId, {
                    method: "POST",
                    body: data
                });
            })
            .then(response => response.text())
            .then(text => {
                addLog('Raw remote answer: ' + text);
                const sdp = atob(text);
                addLog('Remote SDP: ' + sdp);
                pc.setRemoteDescription(new RTCSessionDescription({
                    type: 'answer',
                    sdp: sdp
                }));
                addLog('Done setting sdp');
            })
            .catch(e => {
                addLog(e.toString())
                console.log(e);
            })
    };
    let track: MediaStreamTrack;
    pc.ontrack = ev => {
        addLog('ontrack');
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

