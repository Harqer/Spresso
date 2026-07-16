package com.meta.wearable.retail;

import com.meta.wearable.retail.data.SettingsRepository;
import com.meta.wearable.retail.ui.ProductRepository;
import dagger.MembersInjector;
import dagger.internal.DaggerGenerated;
import dagger.internal.InjectedFieldSignature;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import javax.annotation.processing.Generated;

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
public final class MainActivity_MembersInjector implements MembersInjector<MainActivity> {
  private final Provider<RetailSessionManager> sessionManagerProvider;

  private final Provider<ProductRepository> repositoryProvider;

  private final Provider<SettingsRepository> settingsRepositoryProvider;

  private MainActivity_MembersInjector(Provider<RetailSessionManager> sessionManagerProvider,
      Provider<ProductRepository> repositoryProvider,
      Provider<SettingsRepository> settingsRepositoryProvider) {
    this.sessionManagerProvider = sessionManagerProvider;
    this.repositoryProvider = repositoryProvider;
    this.settingsRepositoryProvider = settingsRepositoryProvider;
  }

  @Override
  public void injectMembers(MainActivity instance) {
    injectSessionManager(instance, sessionManagerProvider.get());
    injectRepository(instance, repositoryProvider.get());
    injectSettingsRepository(instance, settingsRepositoryProvider.get());
  }

  public static MembersInjector<MainActivity> create(
      Provider<RetailSessionManager> sessionManagerProvider,
      Provider<ProductRepository> repositoryProvider,
      Provider<SettingsRepository> settingsRepositoryProvider) {
    return new MainActivity_MembersInjector(sessionManagerProvider, repositoryProvider, settingsRepositoryProvider);
  }

  @InjectedFieldSignature("com.meta.wearable.retail.MainActivity.sessionManager")
  public static void injectSessionManager(MainActivity instance,
      RetailSessionManager sessionManager) {
    instance.sessionManager = sessionManager;
  }

  @InjectedFieldSignature("com.meta.wearable.retail.MainActivity.repository")
  public static void injectRepository(MainActivity instance, ProductRepository repository) {
    instance.repository = repository;
  }

  @InjectedFieldSignature("com.meta.wearable.retail.MainActivity.settingsRepository")
  public static void injectSettingsRepository(MainActivity instance,
      SettingsRepository settingsRepository) {
    instance.settingsRepository = settingsRepository;
  }
}
