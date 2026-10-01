package com.blueprintforge.machine;

import java.util.UUID;

/** A block that currently keeps one blueprint instance out of the world. */
public interface DocumentHolder {
    boolean holdsInstance(UUID instanceId);
}
