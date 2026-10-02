package io.javafast.transport;

final class HttpProtocolHandler implements ProtocolHandler {

    private boolean requestComplete;

    @Override
    public void onBytes(TransportBuffer.Reader reader, int bytes) {
        // TODO:
        // Parse directly from isolated payload buffer.
        // Do not assume that a request fits in one buffer.
        for (int i = 0; i < bytes; i++) {
            final byte b = reader.get();
            System.out.println(b);
        }
    }

    @Override
    public boolean payloadComplete() {
        return this.requestComplete;
    }

    @Override
    public void reset() {
        this.requestComplete = false;
        // TODO reset other things, work buffer, etc.
    }

}
