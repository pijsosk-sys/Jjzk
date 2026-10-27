/** Stands in for the controller's in-flight placement record. */
public final class PendingStub {
    public int sentTick;

    public PendingStub(int sentTick) {
        this.sentTick = sentTick;
    }
}
