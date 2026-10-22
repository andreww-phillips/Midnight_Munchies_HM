import React, { useState } from "react";


export default function RegistrationPage() {
  const [formData, setFormData] = useState({
    role: "",
    email: "",
    password: "",
    // Role-specific fields
    first_name: "",
    last_name: "",
    restaurant_name: "",
    id: "",
    address_longitude: 0.0,       
    address_latitude: 0.0,  
    status: "",
    menu: null, 
    logo: null
  });

  // Handle standard text/select inputs
  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData((prev) => ({
      ...prev,
      [name]: value,
    }));
  };

  // Handle file inputs separately
  const handleFileChange = (e) => {
    const file = e.target.files[0]; // Get the first uploaded file
    setFormData((prev) => ({
      ...prev,
      logo: file,
    }));
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    console.log('Submitting profile & logo file:', formData);
    // Next step: Upload formData.logo to Supabase Storage bucket
  };

  return (
    <>
      <header />
      <nav />
      <main>
        <h3>Register</h3>
        <section id="role-selection-container">
          <label>Select Your Role:</label>
          <select id="role-selection" name="role" value ={formData.role} onChange={handleChange}>
              <option value="">Please select a role</option>
              <option value="customer">Customer</option>
              <option value="courier">Courier</option>
              <option value="restaurant">Restaurant Staff</option>
          </select>
        </section>
        <section id="registration-form-container">
          <div id="input-field" name="email">
            <label htmlFor="email">Email:</label>
            <input type="email" placeholder="Email" name="email" value={formData.email} onChange={handleChange} />
          </div>
          <div id="input-field" placeholder = "Password" name="password" value={formData.password} onChange={handleChange}>
            <label htmlFor="password">Password:</label>
            <input type="password" placeholder="Password" />
          </div>

          {formData.role === "customer" && (
            <>
              <div id="input-field" name="first_name">
                <label htmlFor="first_name">First Name:</label>
                <input type="text" placeholder="First Name" name="first_name" value={formData.first_name} onChange={handleChange} />
              </div>
              <div id="input-field" name="last_name">
                <label htmlFor="last_name">Last Name:</label>
                <input type="text" placeholder="Last Name" name="last_name" value={formData.last_name} onChange={handleChange} />
              </div>
              <div id="input-field" name="address_longitude">
                <label htmlFor="address_longitude">Address Longitude:</label>
                <input type="number" placeholder="Address Longitude" name="address_longitude" value={formData.address_longitude} onChange={handleChange} />
              </div>
              <div id="input-field" name="address_latitude">
                <label htmlFor="address_latitude">Address Latitude:</label>
                <input type="number" placeholder="Address Latitude" name="address_latitude" value={formData.address_latitude} onChange={handleChange} />
              </div>
            </>
            )}
            {formData.role === "restaurant" && (
            <>
              <div id="input-field" name="restaurant_name">
                <label htmlFor="restaurant_name">Restaurant Name:</label>
                <input type="text" placeholder="Restaurant Name" name="restaurant_name" value={formData.restaurant_name} onChange={handleChange} />
              </div>
              <div id="input-field" name="menu">
                <label htmlFor="menu">Menu:</label>
                <input type="file" accept=".pdf,.doc,.docx,.txt" name="menu" onChange={handleChange} />
              </div>
              <div id="input-field" name="logo">
                <label htmlFor="logo">Logo:</label>
                <input type="file" accept=".jpg,.jpeg,.png,.gif,.pdf" name="logo" onChange={handleChange} />
              </div>
            </>
          )}
          {formData.role === "courier" && (
            <>
              <div id="input-field" name="first_name">
                <label htmlFor="first_name">First Name:</label>
                <input type="text" placeholder="First Name" name="first_name" value={formData.first_name} onChange={handleChange} />
              </div>
              <div id="input-field" name="last_name">
                <label htmlFor="last_name">Last Name:</label>
                <input type="text" placeholder="Last Name" name="last_name" value={formData.last_name} onChange={handleChange} />
              </div>
            </>
          )}
          <button type="submit" onClick={handleSubmit}>Register</button>
        </section> 
      </main>
      <footer />
    </>
  );
}