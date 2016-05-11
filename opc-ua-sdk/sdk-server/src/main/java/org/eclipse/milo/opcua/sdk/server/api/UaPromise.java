/*
 * Copyright (c) 2016 Kevin Herron
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Eclipse Distribution License v1.0 which accompany this distribution.
 *
 * The Eclipse Public License is available at
 * 	http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 * 	http://www.eclipse.org/org/documents/edl-v10.html.
 */

package org.eclipse.milo.opcua.sdk.server.api;

import java.util.concurrent.CompletableFuture;

import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;

public class UaPromise<T> {

    private final CompletableFuture<T> future = new CompletableFuture<>();

    public void complete(T value) {
        future.complete(value);
    }

    public void completeExceptionally(long statusCode) {
        completeExceptionally(new StatusCode(statusCode));
    }

    public void completeExceptionally(StatusCode statusCode) {
        completeExceptionally(new UaException(statusCode));
    }

    public void completeExceptionally(UaException exception) {
        future.completeExceptionally(exception);
    }

}
