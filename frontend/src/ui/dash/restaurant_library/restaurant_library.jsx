import React, { useEffect, useState } from "react";
import { supabase } from "../../../supabaseClient"; // adjust path to match your folder
import RestaurantMenu from "../menu_page/menu_page.jsx";
import "./restaurant_library.css";

const BUCKET = "Restaurant Logos"; // the bucket's id, not its display name

const SEED_RESTAURANTS = [
  { restaurant_name: "Bobs Burgers", restaurant_logo: "bobs_burgers_logo" },
  { restaurant_name: "Pizza Palace", restaurant_logo: "pizza_palace_logo" },
  { restaurant_name: "Poutine King", restaurant_logo: "poutine_king_logo" },
];

const logoUrl = (path) =>
  path ? supabase.storage.from(BUCKET).getPublicUrl(path).data.publicUrl : "";

export default function RestaurantList({ role }) {
  const [restaurants, setRestaurants] = useState([]);
  const [loading, setLoading] = useState(true);
  const [selectedRestaurant, setSelectedRestaurant] = useState(null);
  const normalizedRole = role?.toUpperCase();

  const loadRestaurants = async () => {
    setLoading(true);
    const { data, error } = await supabase
      .from("Restaurant")
      .select("id, restaurant_name, restaurant_logo")
      .order("restaurant_name");
    console.log("restaurants:", data, "error:", error);
    if (error) console.error("Error loading restaurants:", error.message);
    else setRestaurants(data);
    setLoading(false);
  };

  // One-time seed: only inserts restaurants that don't exist yet (matched by name)
  const seedRestaurants = async () => {
    const { data: existing, error: readErr } = await supabase
      .from("Restaurant")
      .select("restaurant_name");
    if (readErr) return alert(readErr.message);

    const have = new Set(existing.map((r) => r.restaurant_name));
    const toInsert = SEED_RESTAURANTS.filter((r) => !have.has(r.restaurant_name));
    if (toInsert.length === 0) return alert("Already seeded");

    const { error } = await supabase.from("Restaurant").insert(toInsert);
    if (error) return alert(error.message);

    loadRestaurants();
  };

  useEffect(() => {
    loadRestaurants();
  }, []);

  const openMenu = (restaurant) => {
    setSelectedRestaurant(restaurant);
  };

  return (
    <>
    <main>
      {selectedRestaurant ? (
        <RestaurantMenu
          restaurant={selectedRestaurant}
          role={role}
          onBack={() => setSelectedRestaurant(null)}
        />
      ) : (
        <>
          <h3>Restaurants</h3>

          {loading && <p>Loading...</p>}
          {!loading && restaurants.length === 0 && <p>No restaurants yet.</p>}

          <section id="restaurant-list">
            {restaurants.map((r) => (
              <div className="restaurant-card" key={r.id}>
                <img
                  src={logoUrl(r.restaurant_logo)}
                  alt={`${r.restaurant_name} logo`}
                  width={120}
                  height={120}
                />
                <h4>{r.restaurant_name}</h4>
                <button type="button" name="menu" onClick={() => openMenu(r)}>
                  Menu
                </button>
                {normalizedRole === "COURIER" && (
                  <button type="button" name="orders">Orders</button>
                )}
                {normalizedRole === "RESTAURANT_STAFF" && (
                  <>
                    <button type="button" name="orders">Orders</button>
                    <button type="button" name="edit">Edit</button>
                  </>
                )}
              </div>
            ))}
          </section>
        </>
      )}
    </main>
    <footer>
      <p>&copy; 2023 Midnight Munchies. All rights reserved.</p>
    </footer>
    </>
  );
}