package com.meta.wearable.retail;

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
public final class RetailGlassesActivity_MembersInjector implements MembersInjector<RetailGlassesActivity> {
  private final Provider<RetailSessionManager> sessionManagerProvider;

  private RetailGlassesActivity_MembersInjector(
      Provider<RetailSessionManager> sessionManagerProvider) {
    this.sessionManagerProvider = sessionManagerProvider;
  }

  @Override
  public void injectMembers(RetailGlassesActivity instance) {
    injectSessionManager(instance, sessionManagerProvider.get());
  }

  public static MembersInjector<RetailGlassesActivity> create(
      Provider<RetailSessionManager> sessionManagerProvider) {
    return new RetailGlassesActivity_MembersInjector(sessionManagerProvider);
  }

  @InjectedFieldSignature("com.meta.wearable.retail.RetailGlassesActivity.sessionManager")
  public static void injectSessionManager(RetailGlassesActivity instance,
      RetailSessionManager sessionManager) {
    instance.sessionManager = sessionManager;
  }
}
