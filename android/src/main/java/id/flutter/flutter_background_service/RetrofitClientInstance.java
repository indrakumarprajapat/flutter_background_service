package id.flutter.flutter_background_service;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RetrofitClientInstance {
    private static Retrofit retrofit;
    private static final String BASE_URL = "http://52.66.129.203:3023";
//    private static final String BASE_URL = "http://192.168.0.103:3023";
//    private static final String BASE_URL = "https://prpi.boom123.in";

    public static Retrofit getRetrofitInstance(String token) {
        if (retrofit == null && token != null && token.trim().length() > 10) {
            // Define the interceptor, add authentication headers
            Interceptor interceptor = chain -> {
                Request newRequest = chain.request().newBuilder().
                        addHeader("Authorization", token).build();
                return chain.proceed(newRequest);
            };

            // Add the interceptor to OkHttpClient
            OkHttpClient.Builder builder = new OkHttpClient.Builder();
            builder.interceptors().add(interceptor);
//            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
//            logging.setLevel(HttpLoggingInterceptor.Level.BODY);
//            builder.addInterceptor(logging);  // <-- this is the important line!
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
