package id.flutter.flutter_background_service;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Query;

public interface ApiEndpoints {
    @PUT("dapi/locations")
    Call<DriverLocation> updateDriverLocation(@Body DriverLocation driverLocation, @Query("lat") double lat, @Query("lng") double lng, @Query("driver_id") String driver_id, @Query("trip_id") String trip_id);

    @PUT("dapi/mqstatus")
    Call<DriverLocation> updateDriverMqStatus(@Body DriverLocation driverLocation, @Query("is_mq_alive") int is_mq_alive, @Query("driver_id") String driver_id);

    @PUT("dapi/bgservicestatus")
    Call<DriverLocation> updateBgServiceStatus(@Body DriverLocation driverLocation, @Query("is_bgservice_alive") int is_bgservice_alive, @Query("is_bgservice_stop_manual") int is_bgservice_stop_manual, @Query("driver_id") String driver_id);

    @POST("dapi/trips/reject")
    Call<DriverLocation> rejectRideRequest(@Body RejectRideRequest rejectRideRequest);

    @PUT("dapi/drivers/rides/reject")
    Call<DriverLocation> passRideRequest(@Body PassRideRequest passRideRequest);

//    @POST("dapi/appeventlogs")
//    Call<AppEventLog> addAppEventLog(@Body AppEventLog appEventLog);
}
