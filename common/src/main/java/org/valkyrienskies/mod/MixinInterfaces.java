package org.valkyrienskies.mod;

import org.joml.Matrix4dc;
import org.joml.Matrix4fc;

public final class MixinInterfaces {

    public interface ISetMatrix4fFromJOML {
        void vs$setFromJOML(Matrix4dc m);
        void vs$setFromJOML(Matrix4fc m);
    }
}