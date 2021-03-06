/*******************************************************************************
 * Copyright (c) 2016 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.core.ui.model.utils.imp;

import java.util.Optional;
import java.util.UUID;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlID;

import org.weasis.core.ui.model.utils.UUIDable;

public class DefaultUUID implements UUIDable {
    private static final long serialVersionUID = -3178169761934642523L;

    private String uuid;

    public DefaultUUID() {
        this.uuid = UUID.randomUUID().toString();
    }

    public DefaultUUID(String uuid) {
        setUuid(uuid);
    }

    @Override
    @XmlID
    @XmlAttribute
    public String getUuid() {
        return uuid;
    }

    @Override
    public void setUuid(String uuid) {
        this.uuid = Optional.ofNullable(uuid).orElseGet(UUID.randomUUID()::toString);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((uuid == null) ? 0 : uuid.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!(obj instanceof DefaultUUID))
            return false;
        DefaultUUID other = (DefaultUUID) obj;
        if (!uuid.equals(other.uuid))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "[" + this.uuid + "]";
    }
}
