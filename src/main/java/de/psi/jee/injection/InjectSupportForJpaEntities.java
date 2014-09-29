//******************************************************************
// 
// InjectSupportForJpaEntities.java
// Copyright 2014 PSI AG. All rights reserved.
// This program and the accompanying materials
// are made available under the terms of the GNU Lesser General Public License
// (LGPL) version 3.0 which accompanies this distribution, and is available at
// http://www.gnu.org/licenses/lgpl-3.0.html
// 
// This library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
// Lesser General Public License for more details.
// 
//******************************************************************
package de.psi.jee.injection;

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.inject.Inject;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.persistence.PostLoad;
import javax.persistence.PrePersist;

/**
 * This is entity lifecycle listener aimed towards providing CDI inject
 * capabilities for Entities. To use it you need to annotate your entity or any
 * of it's mapped superclasses with:
 * {@code @EntityListeners ({InjectSupportForJpaEntities.class}) }. It will
 * inject fields declared in superclasses as well.
 * <p>
 * Currently it does not support qualifiers, and any other additional CDI
 * functionality - only basic @Inject on fields is supported (no method
 * injection !).
 * <p>
 * @author akedziora
 */
public class InjectSupportForJpaEntities
{

    /**
     * Method will perform CDI Injection on passed object.
     * <p>
     * @param aJpaEntity on which injection should be performed.
     * @throws PrivilegedActionException @see {@link SetFieldValueAction}
     * @throws NamingException           should not happen unless called with no
     *                                   CDI or JNDI enabled.
     */
    @PrePersist
    @PostLoad
    void PerformCdiInjection (Object aJpaEntity) throws PrivilegedActionException, NamingException
    {
        Collection<Field> fields = extractAllFields (aJpaEntity);
        for (Field field : fields)
        {
            if (field.isAnnotationPresent (Inject.class))
            {
                Object cdiProxyForField = getCdiProxyForField (field.getType ());
                AccessController.doPrivileged (new SetFieldValueAction (field, aJpaEntity, cdiProxyForField));
            }
        }
    }

    private Collection<Field> extractAllFields (Object aEntity) throws SecurityException
    {
        Collection<Field> fields = new LinkedList<Field> ();
        Class<? extends Object> currentClass = aEntity.getClass ();
        while (currentClass.getSuperclass () != null)
        {
            fields.addAll (Arrays.asList (currentClass.getDeclaredFields ()));
            currentClass = currentClass.getSuperclass ();
        }
        return fields;
    }

    private <T> Object getCdiProxyForField (Type aTypeToBeProxied) throws NamingException
    {
        BeanManager bm = getBeanManager ();
        Bean<?> bean = bm.getBeans (aTypeToBeProxied).iterator ().next ();
        return bm.getReference (bean, aTypeToBeProxied, bm.createCreationalContext (bean));
    }

    private BeanManager getBeanManager () throws NamingException
    {
        InitialContext initialContext = new InitialContext ();
        return (BeanManager) initialContext.lookup ("java:comp/BeanManager");

    }

    /**
     * This class is a wrapper for setting field value - as it has to be run in
     * privileged context. essentially it is a runnable with method parameters
     * passed in it's constructor.
     * <p>
     * @See {@link AccessController#doPrivileged(java.security.PrivilegedExceptionAction)
     * }
     */
    private static class SetFieldValueAction implements PrivilegedExceptionAction<Void>
    {

        private final Field field;
        private final Object objectToSetTheFieldOn;
        private final Object value;

        /**
         * Constructor with arguments for the {@link #run() } method.
         * <p>
         * @param aFieldToSet            field which should be set.
         * @param aObjectToSetTheFieldOn on object passed.
         * @param aValue                 that should be set for specified field.
         */
        public SetFieldValueAction (Field aFieldToSet, Object aObjectToSetTheFieldOn, Object aValue)
        {
            this.field = aFieldToSet;
            this.objectToSetTheFieldOn = aObjectToSetTheFieldOn;
            this.value = aValue;
        }

        /**
         * {@inheritDoc }
         * Method forcefully sets the value for the field. It will work even for
         * fields that are private, or otherwise unaccessible provided that will
         * be called in privileged context.
         * <p>
         * @return {@code null}
         * @throws IllegalArgumentException if {@link #value} cannot be cast for
         *                                  {@link #field}
         * @throws IllegalAccessException   if run outside of privileged
         *                                  context.
         */
        @Override
        public Void run () throws IllegalArgumentException, IllegalAccessException
        {
            boolean accessible = field.isAccessible ();
            field.setAccessible (true);
            field.set (objectToSetTheFieldOn, value);
            field.setAccessible (accessible);
            return null;
        }
    }
}
