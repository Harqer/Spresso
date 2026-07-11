package com.meta.wearable.retail.di;

import android.content.Context;
import com.meta.wearable.retail.RetailSessionManager;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast",
    "deprecation",
    "nullness:initialization.field.uninitialized"
})
public final class AppModule_ProvideRetailSessionManagerFactory implements Factory<RetailSessionManager> {
  private final Provider<Context> contextProvider;

  private AppModule_ProvideRetailSessionManagerFactory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public RetailSessionManager get() {
    return provideRetailSessionManager(contextProvider.get());
  }

  public static AppModule_ProvideRetailSessionManagerFactory create(
      Provider<Context> contextProvider) {
    return new AppModule_ProvideRetailSessionManagerFactory(contextProvider);
  }

  public static RetailSessionManager provideRetailSessionManager(Context context) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideRetailSessionManager(context));
  }
}
