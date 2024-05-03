const DELIMITER = "::::"
const MUTE_ON_CONNECT = true
const ICE_SERVERS = [
    { urls: "stun:stun.l.google.com:19302" }
]

navigator.getUserMedia = (navigator.getUserMedia || navigator.webkitGetUserMedia || navigator.mozGetUserMedia || navigator.msGetUserMedia);
const audioCtx = new AudioContext()
audioCtx.suspend()

const remoteGain = audioCtx.createGain()
remoteGain.connect(audioCtx.destination)

const audioOptions = {
    mute: MUTE_ON_CONNECT,
    deaf: false
}

// buttons
const muteToggle = document.createElement("div")
muteToggle.classList.add("button")
muteToggle.classList.add("icon-button")

const muteImage = document.createElement("img")
muteImage.src = MUTE_ON_CONNECT ? "mic-mute.svg" : "mic.svg"
muteImage.classList.add("icon")
muteImage.ondragstart = () => false
muteToggle.append(muteImage)

muteToggle.addEventListener("click", (evt) => {
    audioOptions.mute = !audioOptions.mute
    localStream.gain.gain.setValueAtTime(audioOptions.mute ? 0 : 1, audioCtx.currentTime)
    muteImage.src = audioOptions.mute ? "mic-mute.svg" : "mic.svg"
    console.log("mute", audioOptions.mute)
})

const deafenToggle = document.createElement("div")
deafenToggle.classList.add("button")
deafenToggle.classList.add("icon-button")

const deafenImage = document.createElement("img")
deafenImage.src = "volume-up.svg"
deafenImage.classList.add("icon")
deafenImage.ondragstart = () => false
deafenToggle.append(deafenImage)

deafenToggle.addEventListener("click", (evt) => {
    audioOptions.deaf = !audioOptions.deaf
    remoteGain.gain.setValueAtTime(audioOptions.deaf ? 0 : 1, audioCtx.currentTime)
    deafenImage.src = audioOptions.deaf ? "volume-mute.svg" : "volume-up.svg"
    console.log("deaf", audioOptions.deaf)
})

const enableButton = document.createElement("div")
enableButton.classList.add("button")
enableButton.classList.add("join-button")
enableButton.innerText = "Join Audio"
enableButton.onclick = () => {
    enableButton.remove()
    document.body.append(muteToggle)
    document.body.append(deafenToggle)
    audioCtx.resume()
}


document.body.append(enableButton)
//buttons end

const localStream = {
    stream: null,
    gain: null
}

const remoteStreams = new Map()

const path = ((window.location.protocol === "https:") ? "wss://" : "ws://") + window.location.host + '/upgrade'
const wsc = new WebSocket(path)

function sendEvent(...data) {
    console.log(data)
    wsc.send(data.join(DELIMITER))
}

wsc.onopen = () => {
    console.log("Connected!")
    if (localStream.stream != null) {
        sendEvent("join")
        return
    }

    console.log("Requesting access to mic.")
    navigator.mediaDevices.getUserMedia({ audio: true, video: false })
        .then((stream) => {
            console.log("Mic access granted.")
            const localSource = audioCtx.createMediaStreamSource(stream)
            const localDest = audioCtx.createMediaStreamDestination()
            const localGain = audioCtx.createGain()
            localSource.connect(localGain)
            localGain.connect(localDest)

            localGain.gain.value = MUTE_ON_CONNECT ? 0 : 1
            localStream.stream = localDest.stream
            localStream.gain = localGain
            sendEvent("join")
        })
        .catch((err) => {
            console.log(err)
            alert("You have denied access to your Microphone. You will be unable to use this chat.")
        })
}

wsc.onclose = (evt) => {
    console.log(`Closed ${evt.reason}`)

    remoteStreams.forEach(remote => remote.peer.close())
    remoteStreams.clear()

    audioCtx.close().then(() => console.log("Audio Closed."))
}

wsc.onmessage = ({ data }) => {
    const evt = data.split(DELIMITER)
    console.log(evt)

    switch (evt[0]) {
        case "add_peer":
            handleAddPeer(evt)
            break
        case "get_offer":
            handleGetOffer(evt)
            break
        case "get_answer":
            handleGetAnswer(evt)
            break
        case "candidate":
            handleIceCandidate(evt)
            break
        case "remove_peer":
            handleRemovePeer(evt)
            break
        default:
            console.log("unknown event type", evt[0])
            console.log(evt)
            break
    }
}

wsc.onerror = (err) => {
    console.log(err)
}

function addPeer(peerId) {
    if (peerId == null || remoteStreams.has(peerId)) {
        console.log("Already connected to", peerId)
        return
    }

    const peer = new RTCPeerConnection({ "iceServers": ICE_SERVERS })
    remoteStreams.set(peerId, { stream: new MediaStream(), panner: audioCtx.createPanner(), peer })
    remoteStreams.get(peerId).panner.connect(remoteGain)

    //chrome bug
    var a = new Audio();
    a.muted = true;
    a.srcObject = remoteStreams.get(peerId).stream;
    a.addEventListener('canplaythrough', () => {
        a = null;
    });
    //end of bug

    peer.onicecandidate = (evt) => {
        if (evt.candidate == null) return
        sendEvent("candidate", peerId, JSON.stringify({
            sdpMLineIndex: evt.candidate.sdpMLineIndex,
            candidate: evt.candidate.candidate
        }))
    }

    peer.ontrack = (evt) => {
        const remote = remoteStreams.get(peerId)
        if (remote.stream.getTracks().length > 0) return

        remote.stream.addTrack(evt.track)
        audioCtx.createMediaStreamSource(remote.stream).connect(remote.panner)
    }

    peer.addTrack(localStream.stream.getTracks()[0])
    return peer
}

function handleAddPeer(evt) {
    const peerId = evt[1]
    const peer = addPeer(peerId)
    if (peer == null) return

    peer.createOffer()
        .then((desc) => peer.setLocalDescription(desc).then(() => desc))
        .then((desc) => sendEvent("offer", peerId, JSON.stringify(desc)))
        .catch((err) => console.log(err))
}

function handleGetOffer(evt) {
    const peerId = evt[1]
    const peer = addPeer(peerId)
    if (peer == null) return

    const remoteDesc = new RTCSessionDescription(JSON.parse(evt[2]))
    peer.setRemoteDescription(remoteDesc)
        .then(() => peer.createAnswer())
        .then((desc) => peer.setLocalDescription(desc).then(() => desc))
        .then((desc) => sendEvent("answer", peerId, JSON.stringify(desc)))
        .catch((err) => console.log(err))
}

function handleGetAnswer(evt) {
    const peer = remoteStreams.get(evt[1])?.peer
    if (peer == null) return

    const remoteDesc = new RTCSessionDescription(JSON.parse(evt[2]))
    peer.setRemoteDescription(remoteDesc)
        .catch((err) => console.log(err))
}

function handleIceCandidate(evt) {
    const peer = remoteStreams.get(evt[1])?.peer
    const candidate = JSON.parse(evt[2])
    if (peer == null || candidate == null) return
    peer.addIceCandidate(new RTCIceCandidate(candidate))
}

function handleRemovePeer(evt) {
    const peerId = evt[1]
    if (peerId == null || !remoteStreams.has(peerId)) return
    remoteStreams.get(peerId).panner.disconnect(remoteGain)
    remoteStreams.get(peerId).peer.close()
    remoteStreams.delete(peerId)
}