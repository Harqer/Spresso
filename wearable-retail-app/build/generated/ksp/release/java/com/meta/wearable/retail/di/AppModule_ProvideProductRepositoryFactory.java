package com.meta.wearable.retail.di;

import com.meta.wearable.retail.ui.ProductRepository;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata
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
public final class AppModule_ProvideProductRepositoryFactory implements Factory<ProductRepository> {
  @Override
  public ProductRepository get() {
    return provideProductRepository();
  }

  public static AppModule_ProvideProductRepositoryFactory create() {
    return InstanceHolder.INSTANCE;
  }

  public static ProductRepository provideProductRepository() {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideProductRepository());
  }

  private static final class InstanceHolder {
    static final AppModule_ProvideProductRepositoryFactory INSTANCE = new AppModule_ProvideProductRepositoryFactory();
  }
}
