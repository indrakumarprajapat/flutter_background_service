package id.flutter.flutter_background_service;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RetrofitClientInstance {
    private static Retrofit retrofit;
    private static final String BASE_URL = "http://52.66.129.203:3023";

    public static Retrofit getRetrofitInstance(String token) {
        if (retrofit == null) {
            // Define the interceptor, add authentication headers
            Interceptor interceptor = chain -> {
                Request newRequest = chain.request().newBuilder().
                        addHeader("Authorization", token).build();
                return chain.proceed(newRequest);
            };

            // Add the interceptor to OkHttpClient
            OkHttpClient.Builder builder = new OkHttpClient.Builder();
            builder.interceptors().add(interceptor);
            OkHttpClient client = builder.build();

            // Set the custom client when building adapter
            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .addConverterFactory(GsonConverterFactory.create())
                    .client(client)
                    .build();
        }
        return retrofit;
    }
}
