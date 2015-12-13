package net.n2oapp.framework.config.audit.git.util.mock;

import net.n2oapp.framework.api.util.ToListConsumer;
import net.n2oapp.framework.config.register.ConfigRegister;
import net.n2oapp.framework.config.register.Info;

/**
 * @author dfirstov
 * @since 22.09.2015
 */
public class ConfigRegisterMock extends ConfigRegister {

    private ToListConsumer<Info> consumer;

    public ConfigRegisterMock(ToListConsumer<Info> consumer) {
        this.consumer = consumer;
    }

    @Override
    protected void removeFromCache(Info info) {
        consumer.accept(info);
    }
}
