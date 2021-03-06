package proj.concert.service.services;

import proj.concert.common.dto.*;
import proj.concert.service.domain.Concert;
import proj.concert.service.domain.User;
import proj.concert.service.domain.Performer;
import proj.concert.service.jaxrs.LocalDateTimeParam;
import proj.concert.service.mapper.BookingMapper;
import proj.concert.service.mapper.ConcertMapper;
import proj.concert.service.config.Config;
import proj.concert.service.mapper.PerformerMapper;
import proj.concert.common.types.BookingStatus;
import proj.concert.service.mapper.SeatMapper;
import proj.concert.service.domain.Seat;
import proj.concert.service.domain.Booking;


import javax.persistence.Entity;
import java.util.*;
import javax.persistence.EntityManager;
import javax.persistence.LockModeType;
import javax.persistence.TypedQuery;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.Cookie;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.net.URI;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import java.util.concurrent.ConcurrentHashMap;

import static proj.concert.service.util.TheatreLayout.NUM_SEATS_IN_THEATRE;

@Path("/concert-service")
public class ConcertResource {

    PersistenceManager p = PersistenceManager.instance();
    private static final Map<Long, List<Subscription>> subscribersMap = new ConcurrentHashMap<>();


/*
-using fetch eager as database needs to load all the entities for users to view
-using fetch lazy will cause an error!
 */
    @POST
    @Path("/login")
    public Response login(UserDTO user) {
        EntityManager em = p.createEntityManager();
        User u = null;

        try {
            em.getTransaction().begin(); // start a new transaction
            TypedQuery<User> users = em.createQuery("select a from User a where a.username = :username and a.password = :password", User.class);
            users.setParameter("username", user.getUsername());
            users.setParameter("password", user.getPassword());
            users.setLockMode(LockModeType.PESSIMISTIC_READ);//either optimistic or pessimistic
            //use this query em.createQuery("...").getResultStream().findFirst().orElse(null); java 8 stackoverflow
            u = users.getResultList().stream().findFirst().orElse(null);

            if (u == null) {
                return Response.status(Response.Status.UNAUTHORIZED).build();
            } else {
                NewCookie newCookie = new NewCookie(Config.AUTH_COOKIE, UUID.randomUUID().toString()); //either UUID or we can store the Username for the cookie value
                u.setCookie(newCookie.getValue());
                em.merge(u);
                em.getTransaction().commit();
                return Response.ok().cookie(newCookie).build();
            }
        } finally {
            if (em.getTransaction().isActive()) {
                em.getTransaction().commit();
            }
            em.close();
        }


    }
/*
-retrieve all the concerts
 */

    @GET
    @Path("/concerts")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllConcerts() {
        EntityManager em = PersistenceManager.instance().createEntityManager();
        List<ConcertDTO> dtoconcert = new ArrayList<>();
        try {
            em.getTransaction().begin();
            TypedQuery<Concert> concertQuery = em.createQuery("select a from Concert a", Concert.class); //retrieve concerts
            concertQuery.setLockMode(LockModeType.PESSIMISTIC_READ);
            List<Concert> listconcert = concertQuery.getResultList();

            em.getTransaction().commit();
            for (Concert c : listconcert) {
                dtoconcert.add(ConcertMapper.toConcertDTO(c)); //connects with dto
            }
            return Response.ok(dtoconcert).build();


        } finally {
            if (em.getTransaction().isActive()) {
                em.getTransaction().commit();
            }
            em.close();
        }
    }

    @GET
    @Path("/concerts/summaries")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getConcertSummaries() {
        EntityManager em = p.createEntityManager();
        List<ConcertSummaryDTO> concertS = new ArrayList<>(); //store all the concerts after being "converted" into the dto
        try {
            em.getTransaction().begin();
            TypedQuery<Concert> concertQ = em.createQuery("select a from Concert a", Concert.class);
            concertQ.setLockMode(LockModeType.PESSIMISTIC_READ);
            List<Concert> concerts = concertQ.getResultList();
            if (concerts.isEmpty()) {
                return Response.noContent().build();
            }
            for (Concert c : concerts) {
                concertS.add(ConcertMapper.toConcertSummaryDTO(c));
            }
        } finally {
            if (em.getTransaction().isActive()) {
                em.getTransaction().commit();
            }
            em.close();

        }
        return Response.ok(concertS).build();

    }
/*
retrieve concerts by a specific id
 */
    @GET
    @Path("/concerts/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getConcertById(@PathParam("id") long id) {
        EntityManager em = p.createEntityManager();
        Concert concert;

        try {
            em.getTransaction().begin();
            concert = em.find(Concert.class, id, LockModeType.PESSIMISTIC_READ);

            if (concert == null) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
        } finally {
            if (em.getTransaction().isActive()) {
                em.getTransaction().commit();
            }
            em.close();
        } return Response.ok(ConcertMapper.toConcertDTO(concert)).build();
    }
/*
retrieve all the performers
 */
    @GET
    @Path("/performers")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllPerformers() {
        EntityManager em = p.createEntityManager();
        List<PerformerDTO> performerS = new ArrayList<>();
        try {
            em.getTransaction().begin();
            TypedQuery<Performer> performerQ = em.createQuery("SELECT p FROM Performer p", Performer.class);
            performerQ.setLockMode(LockModeType.PESSIMISTIC_READ);
            List<Performer> performers = performerQ.getResultList();

            em.getTransaction().commit();

            if (performers.isEmpty()) {
                return Response.noContent().build();
            }

            for (Performer p: performers) {
                performerS.add(PerformerMapper.toPerformerDTO(p));
            }
        } finally {
            if (em.getTransaction().isActive()) {
                em.getTransaction().commit();
            }
            em.close();
        }
        return Response.ok(performerS).build();
    }
/*retrieve performers by a specific ID
*/

    @GET
    @Path("/performers/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getPerformers(@PathParam("id") long id) {
        EntityManager em = p.createEntityManager();
        Performer performer;
        try {
            em.getTransaction().begin();
            performer = em.find(Performer.class, id); //find the performer in the performer class

            if (performer == null) { //if performer doesnt exist
                return Response.status(Response.Status.NOT_FOUND).build();
            }
        } finally {
            if (em.getTransaction().isActive()) {
                em.getTransaction().commit();
            } em.close();
        } return Response.ok(PerformerMapper.toPerformerDTO(performer)).build();
    }

    @GET
    @Path("/seats/{date}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getSeats(@PathParam("date") LocalDateTimeParam dTP, @QueryParam("status") BookingStatus stat) {

        List<SeatDTO> seatList = new ArrayList<SeatDTO>();
        EntityManager em = p.createEntityManager();

        try {
            em.getTransaction().begin();
            TypedQuery<Seat> seatQuery = em.createQuery("select s from Seat s where s.date=:date", Seat.class);
            seatQuery.setParameter("date", dTP.getLocalDateTime());
            seatQuery.setLockMode(LockModeType.PESSIMISTIC_READ);

            List<Seat> seatList1 = seatQuery.getResultList();

            em.getTransaction().commit();
            //checking if the seats are available or not
            for (Seat s:seatList1) {
                if (stat.equals(BookingStatus.Any) || (stat.equals(BookingStatus.Unbooked) && !s.isBooked()) || (stat.equals(BookingStatus.Booked) && s.isBooked())) {
                    seatList.add(SeatMapper.toDTO(s));
                }
            }
            GenericEntity<List<SeatDTO>> entity = new GenericEntity<List<SeatDTO>>(seatList) {};
            return Response.ok(entity).build();
        } finally {
            if (em.getTransaction().isActive()) {
                em.getTransaction().commit();
            } em.close();
        }

    }

    private NewCookie appendCookie(Cookie clientCookie) {
        return new NewCookie(Config.AUTH_COOKIE, clientCookie.getValue());
    }
/*
-POST method
-try to make a booking

 */
    @POST
    @Path("/bookings")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response makeBooking(BookingRequestDTO bookingRequestDTO, @CookieParam(Config.AUTH_COOKIE) Cookie cookieId) {
        if (cookieId == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        if(bookingRequestDTO.getSeatLabels().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        EntityManager em = p.createEntityManager();

        try {
            em.getTransaction().begin();

            TypedQuery<User> userQuery = em.createQuery("select u from User u where u.cookie = :cookie", User.class);
            userQuery.setParameter("cookie", cookieId.getValue());
            User user = userQuery.getResultList().stream().findFirst().orElse(null);
            // when user is not authenticated
            if (user == null ) {
                return Response.status(Response.Status.UNAUTHORIZED).build();
            }

            Concert concert = em.find(Concert.class, bookingRequestDTO.getConcertId(), LockModeType.PESSIMISTIC_READ);

            em.getTransaction().commit();
            //when concert is wrong
            if (concert == null) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
            // when date is wrong
            if (!concert.getDates().contains(bookingRequestDTO.getDate())) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }

            em.getTransaction().begin();
            //get the list of seats
            TypedQuery<Seat> seatQuery = em.createQuery("select s from Seat s where s.label in :label and s.date = :date and s.isBooked = false", Seat.class);
            seatQuery.setParameter("label", bookingRequestDTO.getSeatLabels());
            seatQuery.setParameter("date", bookingRequestDTO.getDate());
            seatQuery.setLockMode(LockModeType.PESSIMISTIC_WRITE);
            List<Seat> freeSeat = seatQuery.getResultList();

            if (freeSeat.size() != bookingRequestDTO.getSeatLabels().size()) {
                return Response.status(Response.Status.FORBIDDEN).build();
            }

            Set<Seat> seatSet = new HashSet<>();

            for (Seat s : freeSeat) {
                s.setBooked(true);
                seatSet.add(s);
            }

            Booking booking = new Booking(bookingRequestDTO.getConcertId(), bookingRequestDTO.getDate(), seatSet, user);
            em.persist(booking);

            //number of free seats
            int freeSeats = em.createQuery("SELECT COUNT(s) FROM Seat s WHERE s.date = :date AND s.isBooked = false", Long.class)
                    .setParameter("date", bookingRequestDTO.getDate())
                    .getSingleResult()
                    .intValue();

            //total number of seats
            int allSeats = em.createQuery("SELECT COUNT(s) FROM Seat s WHERE s.date = :date", Long.class)
                    .setParameter("date", bookingRequestDTO.getDate())
                    .getSingleResult()
                    .intValue();

            this.alertSubscribers(freeSeats, allSeats, bookingRequestDTO.getConcertId(), bookingRequestDTO.getDate());

            em.getTransaction().commit();

            return Response.created(URI.create("concert-service/bookings/"+booking.getId())).cookie(appendCookie(cookieId)).build();
        } finally {
            if (em.getTransaction().isActive()) {
                em.getTransaction().commit();
            } em.close();
        }
    }
    /*
    -Get method
    -get the booking by a specific ID
     */

    @GET
    @Path("/bookings/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getBookingById(@PathParam("id") long id, @CookieParam(Config.AUTH_COOKIE) Cookie cookieId) {
        if (cookieId == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        EntityManager em = p.createEntityManager();

        try {
            em.getTransaction().begin();

            Booking booking = em.find(Booking.class, id, LockModeType.PESSIMISTIC_READ);

            em.getTransaction().commit();
            //get the user

            TypedQuery<User> userQuery = em.createQuery("select u from User u where u.cookie = :cookie", User.class);
            userQuery.setParameter("cookie", cookieId.getValue());
            User user = userQuery.getResultList().stream().findFirst().orElse(null);

            if (user == null) {
                return Response.status(Response.Status.UNAUTHORIZED).build();
            }

            if (booking == null) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }

            if (booking.getUser().getId() != user.getId()) {
                return Response.status(Response.Status.FORBIDDEN).build();
            }

            BookingDTO bookingDTO = BookingMapper.toDTO(booking);
            return Response.ok(bookingDTO).cookie(appendCookie(cookieId)).build();
        } finally {
            if (em.getTransaction().isActive()) {
                em.getTransaction().commit();
            } em.close();
        }
    }
/*
Get the booking by the cookie param
 */
    @GET
    @Path("/bookings")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllUserBookings(@CookieParam(Config.AUTH_COOKIE) Cookie cookieId) {
        if (cookieId == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        List<BookingDTO> dtoBookList = new ArrayList<BookingDTO>();
        EntityManager em = p.createEntityManager();

        try {
            em.getTransaction().begin();

            TypedQuery<User> userQuery = em.createQuery("select u from User u where u.cookie = :cookie", User.class);
            userQuery.setParameter("cookie", cookieId.getValue());
            User user = userQuery.getResultList().stream().findFirst().orElse(null);

            if (user == null) {
                return Response.status(Response.Status.UNAUTHORIZED).build();
            }

            TypedQuery<Booking> bookingTypedQuery = em.createQuery("select b from Booking b where b.user =: user", Booking.class);
            bookingTypedQuery.setParameter("user", user);
            List<Booking> bookingList = bookingTypedQuery.getResultList();
            for (Booking booking : bookingList) {
                dtoBookList.add(BookingMapper.toDTO(booking));
            }
            //or we can return a dto bookinglist as a response;
            GenericEntity<List<BookingDTO>> entity = new GenericEntity<List<BookingDTO>>(dtoBookList) {};
            return Response.ok(entity).build();
        } finally {
            if (em.getTransaction().isActive()) {
                em.getTransaction().commit();
            } em.close();
        }
    }

    @POST
    @Path("/subscribe/concertInfo")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public void getConcertInfo(@Suspended AsyncResponse response, @CookieParam(Config.AUTH_COOKIE) Cookie cookieId, ConcertInfoSubscriptionDTO infoDTO) {
        EntityManager em = p.createEntityManager();

        try {
            TypedQuery<User> userQuery = em.createQuery("select u from User u where u.cookie = :cookie", User.class);
            userQuery.setParameter("cookie", cookieId.getValue()); //get the user
            User user = userQuery.getResultList().stream().findFirst().orElse(null);

            if (user == null) {
                response.resume(Response.status(Response.Status.UNAUTHORIZED).build());
                return;
            }

            em.getTransaction().begin();

            Concert concert = em.find(Concert.class, infoDTO.getConcertId(), LockModeType.PESSIMISTIC_READ); //get the concert with the specific concertId

            if (concert == null || concert.getDates().contains(infoDTO.getDate())) {
                response.resume(Response.status(Response.Status.BAD_REQUEST).build());
                return;
            }

            em.getTransaction().commit();

        } finally {
            em.close();
        }

        synchronized (subscribersMap) {
            if (!subscribersMap.containsKey(infoDTO.getDate())) {
                subscribersMap.put(infoDTO.getConcertId(), new LinkedList<>());
            }
        }
        subscribersMap.get(infoDTO.getConcertId()).add(new Subscription(infoDTO, response));

    }


    public void alertSubscribers(int availableSeats, int totalSeats, long concertId, LocalDateTime date){
        List<Subscription> subscriberList = subscribersMap.get(concertId);
        int amountbooked = 100 - (int)(((double)availableSeats / NUM_SEATS_IN_THEATRE) * 100); //percentage of booked seats
        if (subscriberList == null) {
            return;
        }

        List<Subscription> newSubscriptions = new ArrayList<>();

        for (Subscription subscriber : subscriberList) {
            ConcertInfoSubscriptionDTO concertInfoSubscriptionDTO = subscriber.getConcertInfoSubscriptionDTO();
            if (concertInfoSubscriptionDTO.getDate().isEqual(date)){
                if (concertInfoSubscriptionDTO.getPercentageBooked() < amountbooked) {
                    AsyncResponse response = subscriber.getResponse();

                    synchronized (response) {
                        ConcertInfoNotificationDTO notification = new ConcertInfoNotificationDTO(availableSeats);
                        response.resume(Response.ok(notification).build());
                    }
                }
            }
            newSubscriptions.add(subscriber);

        }
        subscribersMap.put(concertId, newSubscriptions);
    }


    // TODO Implement this.

}

class Subscription {

    private ConcertInfoSubscriptionDTO concertSubscriptionDTO;
    private AsyncResponse response;

    public Subscription(ConcertInfoSubscriptionDTO concertSubscriptionDTO, AsyncResponse response){
        this.concertSubscriptionDTO = concertSubscriptionDTO;
        this.response = response;
    }

    public AsyncResponse getResponse(){ return this.response; }
    public ConcertInfoSubscriptionDTO getConcertInfoSubscriptionDTO(){ return this.concertSubscriptionDTO; }

}
