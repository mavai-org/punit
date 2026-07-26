package org.mavai.punit.decl;

import org.mavai.punit.decl.internal.run.DeclaredRun;
import org.mavai.punit.runtime.PUnit.Declared;
import org.mavai.punit.runtime.PUnit.DeclarativeFrontEnd;

/**
 * punit-decl's implementation of the declarative entry seam, discovered
 * by punit-core via {@link java.util.ServiceLoader}.
 */
public final class DeclFrontEnd implements DeclarativeFrontEnd {

    @Override
    public Declared open(Class<?> caller, String methodName, String explicitName) {
        return new DeclaredRun(caller, methodName, explicitName);
    }
}
